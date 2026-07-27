package com.blackserv.passwdgen

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class ScheduledBackupStatus(
    val configured: Boolean = false,
    val enabled: Boolean = false,
    val targetLabel: String = "",
    val wifiOnly: Boolean = true,
    val snapshotUpdatedAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFileName: String? = null,
    val lastError: String? = null,
    val entryCount: Int = 0,
)

internal data class ScheduledBackupDestination(
    val treeUri: Uri,
    val wifiOnly: Boolean,
)

internal data class BackupDocumentRef(
    val documentId: String,
    val displayName: String,
    val lastModified: Long,
)

internal object ScheduledBackupNaming {
    private const val PREFIX = "PasswdGen-auto-"
    private const val SUFFIX = ".pgvault"

    fun fileName(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val formatted = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(epochMillis))
        return "$PREFIX$formatted$SUFFIX"
    }

    fun selectForDeletion(files: List<BackupDocumentRef>, keep: Int = 4): List<BackupDocumentRef> {
        require(keep >= 1)
        return files
            .filter { it.displayName.startsWith(PREFIX) && it.displayName.endsWith(SUFFIX) }
            .sortedWith(
                compareByDescending<BackupDocumentRef> { it.lastModified }
                    .thenByDescending { it.displayName },
            )
            .drop(keep)
    }
}

internal class ScheduledBackupStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    fun status(): ScheduledBackupStatus {
        val configured = preferences.contains(KEY_TREE_URI) &&
            preferences.contains(KEY_WRAPPED_KEY) &&
            preferences.contains(KEY_KEY_IV) &&
            preferences.contains(KEY_SALT)

        return ScheduledBackupStatus(
            configured = configured,
            enabled = configured && preferences.getBoolean(KEY_ENABLED, false),
            targetLabel = preferences.getString(KEY_TARGET_LABEL, "").orEmpty(),
            wifiOnly = preferences.getBoolean(KEY_WIFI_ONLY, true),
            snapshotUpdatedAt = preferences.longOrNull(KEY_SNAPSHOT_UPDATED_AT),
            lastAttemptAt = preferences.longOrNull(KEY_LAST_ATTEMPT_AT),
            lastSuccessAt = preferences.longOrNull(KEY_LAST_SUCCESS_AT),
            lastFileName = preferences.getString(KEY_LAST_FILE_NAME, null),
            lastError = preferences.getString(KEY_LAST_ERROR, null),
            entryCount = preferences.getInt(KEY_ENTRY_COUNT, 0),
        )
    }

    fun saveConfiguration(
        treeUri: Uri,
        targetLabel: String,
        wifiOnly: Boolean,
        material: VaultBackupKeyMaterial,
    ) {
        val wrapped = wrap(material.keyBytes)
        try {
            preferences.edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .putString(KEY_TARGET_LABEL, targetLabel.take(256))
                .putBoolean(KEY_WIFI_ONLY, wifiOnly)
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_SALT, material.salt.toBase64())
                .putInt(KEY_ITERATIONS, material.iterations)
                .putString(KEY_WRAPPED_KEY, wrapped.cipherText.toBase64())
                .putString(KEY_KEY_IV, wrapped.iv.toBase64())
                .remove(KEY_SNAPSHOT_UPDATED_AT)
                .remove(KEY_LAST_ATTEMPT_AT)
                .remove(KEY_LAST_SUCCESS_AT)
                .remove(KEY_LAST_FILE_NAME)
                .remove(KEY_LAST_ERROR)
                .putInt(KEY_ENTRY_COUNT, 0)
                .apply()
        } finally {
            wrapped.cipherText.fill(0)
            wrapped.iv.fill(0)
        }
    }

    fun loadKeyMaterial(): VaultBackupKeyMaterial? {
        if (!status().configured) return null
        val salt = preferences.getString(KEY_SALT, null)?.fromBase64() ?: return null
        val cipherText = preferences.getString(KEY_WRAPPED_KEY, null)?.fromBase64() ?: return null
        val iv = preferences.getString(KEY_KEY_IV, null)?.fromBase64() ?: return null
        val iterations = preferences.getInt(KEY_ITERATIONS, VaultBackupCodec.DEFAULT_ITERATIONS)
        val keyBytes = try {
            unwrap(cipherText, iv)
        } finally {
            cipherText.fill(0)
            iv.fill(0)
        }
        return VaultBackupKeyMaterial(salt = salt, iterations = iterations, keyBytes = keyBytes)
    }

    fun destination(): ScheduledBackupDestination? {
        val status = status()
        if (!status.enabled) return null
        val uri = treeUri() ?: return null
        return ScheduledBackupDestination(uri, status.wifiOnly)
    }

    fun treeUri(): Uri? = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun recordSnapshot(entryCount: Int, timestamp: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(KEY_SNAPSHOT_UPDATED_AT, timestamp)
            .putInt(KEY_ENTRY_COUNT, entryCount)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun recordAttempt(timestamp: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(KEY_LAST_ATTEMPT_AT, timestamp).apply()
    }

    fun recordSuccess(fileName: String, timestamp: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(KEY_LAST_SUCCESS_AT, timestamp)
            .putString(KEY_LAST_FILE_NAME, fileName)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun recordFailure(message: String, disableSchedule: Boolean = false) {
        val editor = preferences.edit().putString(KEY_LAST_ERROR, message.take(300))
        if (disableSchedule) editor.putBoolean(KEY_ENABLED, false)
        editor.apply()
    }

    fun clear() {
        preferences.edit().clear().commit()
        runCatching {
            if (keyStore.containsAlias(WRAPPING_KEY_ALIAS)) keyStore.deleteEntry(WRAPPING_KEY_ALIAS)
        }
    }

    fun registerStatusListener(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChanged() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterStatusListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun wrap(value: ByteArray): WrappedValue {
        require(value.size == 32) { "Nieprawidłowy klucz kopii." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        cipher.updateAAD(WRAPPING_AAD)
        return WrappedValue(cipher.doFinal(value), cipher.iv)
    }

    private fun unwrap(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateWrappingKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(WRAPPING_AAD)
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        (keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUnlockedDeviceRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun SharedPreferences.longOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L).takeIf { it > 0L } else null

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private data class WrappedValue(val cipherText: ByteArray, val iv: ByteArray)

    private companion object {
        const val PREFERENCES_NAME = "passwdgen.scheduled-backup.v1"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_TARGET_LABEL = "target_label"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_ENABLED = "enabled"
        const val KEY_SALT = "salt"
        const val KEY_ITERATIONS = "iterations"
        const val KEY_WRAPPED_KEY = "wrapped_key"
        const val KEY_KEY_IV = "key_iv"
        const val KEY_SNAPSHOT_UPDATED_AT = "snapshot_updated_at"
        const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
        const val KEY_LAST_SUCCESS_AT = "last_success_at"
        const val KEY_LAST_FILE_NAME = "last_file_name"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_ENTRY_COUNT = "entry_count"

        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val WRAPPING_KEY_ALIAS = "passwdgen.backup.schedule.wrap.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        val WRAPPING_AAD = "passwdgen-scheduled-backup-key-v1".encodeToByteArray()
    }
}

internal class ScheduledBackupCoordinator(context: Context) {
    private val applicationContext = context.applicationContext
    private val store = ScheduledBackupStore(applicationContext)

    fun status(): ScheduledBackupStatus = store.status()

    fun registerStatusListener(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        store.registerStatusListener(onChanged)

    fun unregisterStatusListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        store.unregisterStatusListener(listener)

    fun configure(
        treeUri: Uri,
        targetLabel: String,
        wifiOnly: Boolean,
        passphrase: String,
        entries: List<VaultEntry>,
    ) {
        val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        applicationContext.contentResolver.takePersistableUriPermission(treeUri, permissionFlags)

        val material = VaultBackupCodec.prepareKey(passphrase)
        try {
            store.saveConfiguration(treeUri, targetLabel, wifiOnly, material)
            writeSnapshot(entries, material)
            store.recordSnapshot(entries.size)
            ScheduledBackupScheduler.schedule(applicationContext, wifiOnly)
            ScheduledBackupScheduler.enqueueNow(applicationContext, wifiOnly)
        } catch (error: Throwable) {
            runCatching {
                applicationContext.contentResolver.releasePersistableUriPermission(treeUri, permissionFlags)
            }
            deleteSnapshot()
            store.clear()
            throw error
        } finally {
            material.close()
        }
    }

    fun refreshSnapshotIfConfigured(entries: List<VaultEntry>): Boolean {
        val material = store.loadKeyMaterial() ?: return false
        return try {
            writeSnapshot(entries, material)
            store.recordSnapshot(entries.size)
            true
        } catch (error: Throwable) {
            store.recordFailure("Nie udało się odświeżyć zaszyfrowanego snapshotu kopii.")
            throw error
        } finally {
            material.close()
        }
    }

    fun enqueueNow(entries: List<VaultEntry>): UUID {
        val status = store.status()
        check(status.enabled) { "Automatyczna kopia nie jest skonfigurowana." }
        check(refreshSnapshotIfConfigured(entries)) { "Brak klucza automatycznej kopii." }
        return ScheduledBackupScheduler.enqueueNow(applicationContext, status.wifiOnly)
    }

    fun disable() {
        val treeUri = store.treeUri()
        ScheduledBackupScheduler.cancel(applicationContext)
        treeUri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                applicationContext.contentResolver.releasePersistableUriPermission(it, flags)
            }
        }
        deleteSnapshot()
        store.clear()
    }

    private fun writeSnapshot(entries: List<VaultEntry>, material: VaultBackupKeyMaterial) {
        val encoded = VaultBackupCodec.encodeWithKey(entries, material)
        val target = snapshotFile(applicationContext)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            target.parentFile?.mkdirs()
            java.io.FileOutputStream(temporary).use { output ->
                output.write(encoded)
                output.flush()
                output.fd.sync()
            }
            try {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            encoded.fill(0)
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun deleteSnapshot() {
        runCatching { snapshotFile(applicationContext).delete() }
    }

    companion object {
        internal fun snapshotFile(context: Context): File =
            File(File(context.filesDir, "scheduled-backup"), "latest.pgvault")
    }
}

internal object ScheduledBackupScheduler {
    private const val UNIQUE_PERIODIC_WORK = "passwdgen-weekly-backup-v1"
    private const val UNIQUE_IMMEDIATE_WORK = "passwdgen-backup-now-v1"

    fun schedule(context: Context, wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<ScheduledVaultBackupWorker>(
            7,
            TimeUnit.DAYS,
            1,
            TimeUnit.DAYS,
        )
            .setInitialDelay(7, TimeUnit.DAYS)
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(context: Context, wifiOnly: Boolean): UUID {
        val request = OneTimeWorkRequestBuilder<ScheduledVaultBackupWorker>()
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request.id
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
    }

    private fun constraints(wifiOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
}

internal class ScheduledVaultBackupWorker(
    applicationContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(applicationContext, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = ScheduledBackupStore(applicationContext)
        val destination = store.destination() ?: return@withContext Result.success()
        val snapshot = ScheduledBackupCoordinator.snapshotFile(applicationContext)
        if (!snapshot.isFile || snapshot.length() <= 0L || snapshot.length() > MAX_BACKUP_BYTES) {
            store.recordFailure("Brak aktualnego zaszyfrowanego snapshotu kopii.")
            return@withContext Result.failure()
        }

        store.recordAttempt()
        try {
            val fileName = uploadSnapshot(destination.treeUri, snapshot)
            rotateOldBackups(destination.treeUri)
            store.recordSuccess(fileName)
            Result.success()
        } catch (error: SecurityException) {
            store.recordFailure("Utracono dostęp do folderu kopii. Wybierz folder ponownie.", disableSchedule = true)
            Result.failure()
        } catch (error: IOException) {
            val message = if (runAttemptCount < MAX_RETRY_ATTEMPTS - 1) {
                "Nie udało się zapisać kopii. Android spróbuje ponownie."
            } else {
                "Nie udało się zapisać kopii po kilku próbach."
            }
            store.recordFailure(message)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS - 1) Result.retry() else Result.failure()
        } catch (error: Exception) {
            store.recordFailure("Automatyczna kopia zakończyła się błędem.")
            Result.failure()
        }
    }

    private fun uploadSnapshot(treeUri: Uri, snapshot: File): String {
        val resolver = applicationContext.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val fileName = ScheduledBackupNaming.fileName(System.currentTimeMillis())
        val documentUri = DocumentsContract.createDocument(
            resolver,
            parentUri,
            VaultBackupCodec.MIME_TYPE,
            fileName,
        ) ?: throw IOException("Dostawca plików nie utworzył dokumentu kopii.")

        try {
            resolver.openOutputStream(documentUri, "w")?.use { output ->
                snapshot.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            } ?: throw IOException("Nie udało się otworzyć dokumentu kopii do zapisu.")

            val localHash = snapshot.inputStream().use(::sha256)
            val remoteHash = resolver.openInputStream(documentUri)?.use(::sha256)
                ?: throw IOException("Nie udało się zweryfikować zapisanej kopii.")
            if (!localHash.contentEquals(remoteHash)) {
                throw IOException("Weryfikacja zapisanej kopii nie powiodła się.")
            }
            return fileName
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }
            throw error
        }
    }

    private fun rotateOldBackups(treeUri: Uri) {
        runCatching {
            val resolver = applicationContext.contentResolver
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            val documents = mutableListOf<BackupDocumentRef>()
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    documents += BackupDocumentRef(
                        documentId = cursor.getString(idIndex),
                        displayName = cursor.getString(nameIndex).orEmpty(),
                        lastModified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex),
                    )
                }
            }

            ScheduledBackupNaming.selectForDeletion(documents).forEach { document ->
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, document.documentId)
                DocumentsContract.deleteDocument(resolver, uri)
            }
        }
    }

    private fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest()
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 16L * 1024L * 1024L
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
