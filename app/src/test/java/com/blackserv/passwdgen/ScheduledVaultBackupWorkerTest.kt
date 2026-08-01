package com.blackserv.passwdgen

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.time.ZoneOffset

class ScheduledVaultBackupWorkerTest {
    @Test
    fun transferWritesRereadsVerifiesAndReturnsName() {
        val store = FakeScheduledBackupDocumentStore()
        val snapshot = temporarySnapshot(ByteArray(8_192) { (it % 251).toByte() })
        val transfer = ScheduledBackupTransfer(store, now = { 0L }, zoneId = ZoneOffset.UTC)

        val fileName = transfer.uploadAndRotate(snapshot)

        assertEquals("PasswdGen-auto-19700101-000000.pgvault", fileName)
        assertEquals(1, store.documents.size)
        assertArrayEquals(snapshot.readBytes(), store.documents.single().bytes)
        assertTrue(store.readCount > 0)
    }

    @Test
    fun transferRotatesOnlyApplicationBackups() {
        val store = FakeScheduledBackupDocumentStore().apply {
            add("oldest", "PasswdGen-auto-20260101-000000.pgvault", 1L, byteArrayOf(1))
            add("older", "PasswdGen-auto-20260201-000000.pgvault", 2L, byteArrayOf(2))
            add("keep-1", "PasswdGen-auto-20260301-000000.pgvault", 3L, byteArrayOf(3))
            add("keep-2", "PasswdGen-auto-20260401-000000.pgvault", 4L, byteArrayOf(4))
            add("keep-3", "PasswdGen-auto-20260501-000000.pgvault", 5L, byteArrayOf(5))
            add("manual", "PasswdGen-backup-manual.pgvault", 0L, byteArrayOf(9))
        }
        val snapshot = temporarySnapshot(byteArrayOf(7, 8, 9))
        val transfer = ScheduledBackupTransfer(store, now = { 7L }, zoneId = ZoneOffset.UTC)

        transfer.uploadAndRotate(snapshot)

        assertEquals(4, store.documents.count { it.name.startsWith("PasswdGen-auto-") })
        assertTrue(store.documents.any { it.id == "manual" })
        assertFalse(store.documents.any { it.id == "oldest" })
        assertFalse(store.documents.any { it.id == "older" })
    }

    @Test
    fun checksumMismatchDeletesPartialRemoteDocument() {
        val store = FakeScheduledBackupDocumentStore().apply { corruptReads = true }
        val snapshot = temporarySnapshot(ByteArray(256) { it.toByte() })
        val transfer = ScheduledBackupTransfer(store, now = { 0L }, zoneId = ZoneOffset.UTC)

        val error = runCatching { transfer.uploadAndRotate(snapshot) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(store.documents.isEmpty())
        assertEquals(1, store.deletedIds.size)
    }

    @Test
    fun noSpaceFailureDeletesCreatedDocument() {
        val store = FakeScheduledBackupDocumentStore().apply {
            writeFailure = IOException("No space left on device")
        }
        val snapshot = temporarySnapshot(ByteArray(128) { 42 })
        val transfer = ScheduledBackupTransfer(store, now = { 0L }, zoneId = ZoneOffset.UTC)

        val error = runCatching { transfer.uploadAndRotate(snapshot) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(store.documents.isEmpty())
        assertEquals(1, store.deletedIds.size)
    }

    @Test
    fun permissionFailureIsPreservedForWorkerPolicy() {
        val store = FakeScheduledBackupDocumentStore().apply {
            createFailure = SecurityException("Permission revoked")
        }
        val snapshot = temporarySnapshot(byteArrayOf(1, 2, 3))
        val transfer = ScheduledBackupTransfer(store, now = { 0L }, zoneId = ZoneOffset.UTC)

        val error = runCatching { transfer.uploadAndRotate(snapshot) }.exceptionOrNull()
        val decision = ScheduledBackupWorkerPolicy.forFailure(requireNotNull(error), runAttemptCount = 0)

        assertEquals(ScheduledBackupWorkDisposition.FAILURE, decision.disposition)
        assertTrue(decision.disableSchedule)
        assertEquals("Utracono dostęp do folderu kopii. Wybierz folder ponownie.", decision.message)
    }

    @Test
    fun ioFailureRetriesOnlyBeforeFinalAttempt() {
        val first = ScheduledBackupWorkerPolicy.forFailure(IOException("temporary"), runAttemptCount = 0)
        val final = ScheduledBackupWorkerPolicy.forFailure(IOException("still failing"), runAttemptCount = 2)

        assertEquals(ScheduledBackupWorkDisposition.RETRY, first.disposition)
        assertFalse(first.disableSchedule)
        assertEquals(ScheduledBackupWorkDisposition.FAILURE, final.disposition)
        assertFalse(final.disableSchedule)
    }

    @Test
    fun unexpectedFailureDoesNotRetry() {
        val decision = ScheduledBackupWorkerPolicy.forFailure(IllegalStateException("boom"), runAttemptCount = 0)

        assertEquals(ScheduledBackupWorkDisposition.FAILURE, decision.disposition)
        assertFalse(decision.disableSchedule)
        assertEquals("Automatyczna kopia zakończyła się błędem.", decision.message)
    }

    private fun temporarySnapshot(bytes: ByteArray): File =
        Files.createTempFile("passwdgen-snapshot-", ".pgvault").toFile().apply {
            deleteOnExit()
            writeBytes(bytes)
        }
}

private data class FakeDocument(
    val id: String,
    val name: String,
    val modified: Long,
    var bytes: ByteArray,
)

private class FakeScheduledBackupDocumentStore : ScheduledBackupDocumentStore {
    val documents = mutableListOf<FakeDocument>()
    val deletedIds = mutableListOf<String>()
    var createFailure: Throwable? = null
    var writeFailure: Throwable? = null
    var corruptReads: Boolean = false
    var readCount: Int = 0
    private var sequence = 0

    override fun create(displayName: String): String {
        createFailure?.let { throw it }
        val id = "created-${sequence++}"
        documents += FakeDocument(id, displayName, Long.MAX_VALUE - sequence, byteArrayOf())
        return id
    }

    override fun write(documentId: String, source: InputStream) {
        writeFailure?.let { throw it }
        document(documentId).bytes = source.readBytes()
    }

    override fun read(documentId: String): InputStream {
        readCount += 1
        val bytes = document(documentId).bytes.copyOf()
        if (corruptReads && bytes.isNotEmpty()) bytes[bytes.lastIndex] = (bytes.last() xor 0x01)
        return ByteArrayInputStream(bytes)
    }

    override fun list(): List<BackupDocumentRef> = documents.map {
        BackupDocumentRef(it.id, it.name, it.modified)
    }

    override fun delete(documentId: String) {
        deletedIds += documentId
        documents.removeAll { it.id == documentId }
    }

    fun add(id: String, name: String, modified: Long, bytes: ByteArray) {
        documents += FakeDocument(id, name, modified, bytes)
    }

    private fun document(id: String): FakeDocument = documents.first { it.id == id }
}

private infix fun Byte.xor(other: Int): Byte = (toInt() xor other).toByte()
