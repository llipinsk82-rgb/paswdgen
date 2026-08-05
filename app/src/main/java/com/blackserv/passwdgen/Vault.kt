package com.blackserv.passwdgen

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class AndroidAppBinding(
    val packageName: String,
    val signerSha256: String,
)

internal data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    val service: String,
    val website: String = "",
    val username: String,
    val password: String,
    val notes: String = "",
    val androidApps: List<AndroidAppBinding> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

private data class EncryptedRecord(
    val id: String,
    val cipherText: ByteArray,
    val iv: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class CipherPayload(
    val cipherText: ByteArray,
    val iv: ByteArray,
)

internal class VaultCrypto {
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    fun probe() {
        val encrypted = encrypt("vault-probe".encodeToByteArray(), PROBE_AAD)
        check(decrypt(encrypted.cipherText, encrypted.iv, PROBE_AAD).decodeToString() == "vault-probe")
    }

    fun encrypt(plainText: ByteArray, aad: ByteArray): CipherPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad)
        return CipherPayload(cipher.doFinal(plainText), cipher.iv)
    }

    fun decrypt(cipherText: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                AUTH_VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
            .setUnlockedDeviceRequired(true)
            .build()

        keyGenerator.init(specification)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "passwdgen.vault.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val AUTH_VALIDITY_SECONDS = 300
        val PROBE_AAD = "passwdgen-probe".encodeToByteArray()
    }
}

private class VaultDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE vault_entries (
                id TEXT PRIMARY KEY NOT NULL,
                cipher_text BLOB NOT NULL,
                iv BLOB NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_vault_updated_at ON vault_entries(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Brak migracji bazy z wersji $oldVersion do $newVersion. Destrukcyjna migracja jest zabroniona.")
    }

    fun all(): List<EncryptedRecord> {
        readableDatabase.query(
            "vault_entries",
            arrayOf("id", "cipher_text", "iv", "created_at", "updated_at"),
            null,
            null,
            null,
            null,
            "updated_at DESC",
        ).use { cursor ->
            val records = mutableListOf<EncryptedRecord>()
            while (cursor.moveToNext()) {
                records += EncryptedRecord(
                    id = cursor.getString(0),
                    cipherText = cursor.getBlob(1),
                    iv = cursor.getBlob(2),
                    createdAt = cursor.getLong(3),
                    updatedAt = cursor.getLong(4),
                )
            }
            return records
        }
    }

    fun upsert(record: EncryptedRecord) {
        upsert(writableDatabase, record)
    }

    fun upsertAll(records: List<EncryptedRecord>) {
        if (records.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            records.forEach { upsert(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun upsert(db: SQLiteDatabase, record: EncryptedRecord) {
        val values = ContentValues().apply {
            put("id", record.id)
            put("cipher_text", record.cipherText)
            put("iv", record.iv)
            put("created_at", record.createdAt)
            put("updated_at", record.updatedAt)
        }
        db.insertWithOnConflict(
            "vault_entries",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun delete(id: String) {
        writableDatabase.delete("vault_entries", "id = ?", arrayOf(id))
    }

    private companion object {
        const val DATABASE_NAME = "vault.db"
        const val DATABASE_VERSION = 1
    }
}

internal class VaultRepository(context: Context) {
    private val crypto = VaultCrypto()
    private val database = VaultDatabase(context.applicationContext)

    fun unlockProbe() = crypto.probe()

    fun loadAll(): List<VaultEntry> = database.all().map { record ->
        val plainText = crypto.decrypt(
            cipherText = record.cipherText,
            iv = record.iv,
            aad = record.id.encodeToByteArray(),
        )
        decode(record, plainText.decodeToString())
    }

    fun save(entry: VaultEntry) {
        val normalized = normalize(entry, updatedAt = System.currentTimeMillis())
        database.upsert(encrypt(normalized))
    }

    fun importEntries(entries: List<VaultEntry>): Int {
        require(entries.size <= 10_000) { "Kopia zawiera zbyt wiele wpisów." }
        val currentById = loadAll().associateBy(VaultEntry::id)
        val accepted = entries.map { normalize(it, updatedAt = it.updatedAt) }
            .filter { imported ->
                val current = currentById[imported.id]
                current == null || imported.updatedAt > current.updatedAt
            }
        database.upsertAll(accepted.map(::encrypt))
        return accepted.size
    }

    fun delete(id: String) = database.delete(id)

    private fun normalize(entry: VaultEntry, updatedAt: Long): VaultEntry {
        require(entry.id.isNotBlank() && entry.id.length <= 128) { "Nieprawidłowy identyfikator wpisu." }
        require(entry.service.isNotBlank()) { "Nazwa usługi jest wymagana." }
        require(entry.username.isNotBlank()) { "Login jest wymagany." }
        require(entry.password.isNotBlank()) { "Hasło jest wymagane." }
        require(entry.createdAt > 0L && updatedAt >= entry.createdAt) { "Nieprawidłowe daty wpisu." }
        require(entry.androidApps.size <= MAX_APP_BINDINGS) { "Wpis ma zbyt wiele powiązanych aplikacji." }

        val normalizedBindings = entry.androidApps.map { binding ->
            val packageName = binding.packageName.trim().lowercase(Locale.ROOT)
            val signer = binding.signerSha256
                .trim()
                .lowercase(Locale.ROOT)
                .replace(":", "")
            require(PACKAGE_NAME.matches(packageName)) { "Nieprawidłowa nazwa pakietu aplikacji." }
            require(SIGNER_SET.matches(signer)) { "Nieprawidłowy certyfikat aplikacji." }
            AndroidAppBinding(packageName, signer)
        }.distinct().sortedWith(compareBy(AndroidAppBinding::packageName, AndroidAppBinding::signerSha256))

        return entry.copy(
            service = entry.service.trim(),
            website = entry.website.trim(),
            username = entry.username.trim(),
            notes = entry.notes.trim(),
            androidApps = normalizedBindings,
            updatedAt = updatedAt,
        )
    }

    private fun encrypt(entry: VaultEntry): EncryptedRecord {
        val payload = encode(entry)
        val encrypted = crypto.encrypt(payload.encodeToByteArray(), entry.id.encodeToByteArray())
        return EncryptedRecord(
            id = entry.id,
            cipherText = encrypted.cipherText,
            iv = encrypted.iv,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
        )
    }

    private fun encode(entry: VaultEntry): String = JSONObject()
        .put("service", entry.service)
        .put("website", entry.website)
        .put("username", entry.username)
        .put("password", entry.password)
        .put("notes", entry.notes)
        .put(
            "androidApps",
            JSONArray().apply {
                entry.androidApps.forEach { binding ->
                    put(
                        JSONObject()
                            .put("packageName", binding.packageName)
                            .put("signerSha256", binding.signerSha256),
                    )
                }
            },
        )
        .toString()

    private fun decode(record: EncryptedRecord, json: String): VaultEntry {
        val value = JSONObject(json)
        return VaultEntry(
            id = record.id,
            service = value.getString("service"),
            website = value.optString("website"),
            username = value.getString("username"),
            password = value.getString("password"),
            notes = value.optString("notes"),
            androidApps = decodeBindings(value.optJSONArray("androidApps")),
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
    }

    private fun decodeBindings(values: JSONArray?): List<AndroidAppBinding> {
        if (values == null) return emptyList()
        require(values.length() <= MAX_APP_BINDINGS) { "Wpis ma zbyt wiele powiązanych aplikacji." }
        return buildList(values.length()) {
            repeat(values.length()) { index ->
                val item = values.getJSONObject(index)
                add(
                    AndroidAppBinding(
                        packageName = item.getString("packageName"),
                        signerSha256 = item.getString("signerSha256"),
                    ),
                )
            }
        }
    }

    private companion object {
        const val MAX_APP_BINDINGS = 32
        val PACKAGE_NAME = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
        val SIGNER_SET = Regex("^[0-9a-f]{64}(,[0-9a-f]{64})*$")
    }
}
