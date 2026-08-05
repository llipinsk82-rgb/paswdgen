package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class ScheduledBackupPolicyTest {
    @Test
    fun generatedNameIsStableAndRecognizable() {
        assertEquals(
            "PasswdGen-auto-19700101-000000.pgvault",
            ScheduledBackupNaming.fileName(0L, ZoneOffset.UTC),
        )
    }

    @Test
    fun rotationKeepsFourNewestApplicationBackupsOnly() {
        val files = listOf(
            BackupDocumentRef("oldest", "PasswdGen-auto-20260101-000000.pgvault", 1_000),
            BackupDocumentRef("older", "PasswdGen-auto-20260201-000000.pgvault", 2_000),
            BackupDocumentRef("keep-1", "PasswdGen-auto-20260301-000000.pgvault", 3_000),
            BackupDocumentRef("keep-2", "PasswdGen-auto-20260401-000000.pgvault", 4_000),
            BackupDocumentRef("keep-3", "PasswdGen-auto-20260501-000000.pgvault", 5_000),
            BackupDocumentRef("keep-4", "PasswdGen-auto-20260601-000000.pgvault", 6_000),
            BackupDocumentRef("manual", "PasswdGen-backup-20260101.pgvault", 500),
            BackupDocumentRef("other", "notes.txt", 100),
        )

        assertEquals(
            listOf("older", "oldest"),
            ScheduledBackupNaming.selectForDeletion(files).map(BackupDocumentRef::documentId),
        )
    }
}
