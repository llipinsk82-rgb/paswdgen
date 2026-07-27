package com.blackserv.passwdgen

import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object VaultBackupCodec {
    private const val MAGIC = "PASSWDGEN-BACKUP"
    private const val FORMAT_VERSION = 1
    private const val KDF_NAME = "PBKDF2-HMAC-SHA256"
    private const val CIPHER_NAME = "AES-256-GCM"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_FACTORY = "PBKDF2WithHmacSHA256"
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private const val MIN_PASSPHRASE_LENGTH = 12
    private const val MAX_PASSPHRASE_LENGTH = 256
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 2_000_000

    internal const val DEFAULT_ITERATIONS = 600_000
    internal const val FILE_EXTENSION = "pgvault"
    internal const val MIME_TYPE = "application/vnd.blackserv.passwdgen.vault-backup"

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun encode(
        entries: List<VaultEntry>,
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        validatePassphrase(passphrase)
        require(entries.size <= MAX_ENTRIES) { "Kopia zawiera zbyt wiele wpisów." }
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Nieprawidłowy parametr zabezpieczenia kopii."
        }

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val aad = associatedData(FORMAT_VERSION, iterations)
        val plainText = encodePayload(entries)
        val keyBytes = deriveKey(passphrase, salt, iterations)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.updateAAD(aad)
            val cipherText = cipher.doFinal(plainText)

            JSONObject()
                .put("magic", MAGIC)
                .put("version", FORMAT_VERSION)
                .put("kdf", KDF_NAME)
                .put("iterations", iterations)
                .put("salt", encoder.encodeToString(salt))
                .put("cipher", CIPHER_NAME)
                .put("iv", encoder.encodeToString(iv))
                .put("payload", encoder.encodeToString(cipherText))
                .toString()
                .encodeToByteArray()
                .also { require(it.size <= MAX_BACKUP_BYTES) { "Kopia jest zbyt duża." } }
        } finally {
            plainText.fill(0)
            keyBytes.fill(0)
        }
    }

    fun decode(data: ByteArray, passphrase: String): List<VaultEntry> {
        validatePassphrase(passphrase)
        require(data.isNotEmpty() && data.size <= MAX_BACKUP_BYTES) { "Nieprawidłowy rozmiar kopii." }

        val envelope = runCatching { JSONObject(data.decodeToString()) }
            .getOrElse { throw IllegalArgumentException("Plik nie jest prawidłową kopią PasswdGen.") }

        require(envelope.optString("magic") == MAGIC) { "Plik nie jest kopią PasswdGen." }
        val version = envelope.optInt("version", -1)
        require(version == FORMAT_VERSION) { "Nieobsługiwana wersja kopii: $version." }
        require(envelope.optString("kdf") == KDF_NAME) { "Nieobsługiwany algorytm KDF." }
        require(envelope.optString("cipher") == CIPHER_NAME) { "Nieobsługiwany algorytm szyfrowania." }

        val iterations = envelope.optInt("iterations", -1)
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Nieprawidłowy parametr zabezpieczenia kopii."
        }

        val salt = decodeBase64(envelope, "salt", SALT_BYTES)
        val iv = decodeBase64(envelope, "iv", IV_BYTES)
        val cipherText = decodeBase64(envelope, "payload", expectedSize = null)
        require(cipherText.size >= 16 && cipherText.size <= MAX_BACKUP_BYTES) { "Uszkodzona zawartość kopii." }

        val keyBytes = deriveKey(passphrase, salt, iterations)
        val plainText = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.updateAAD(associatedData(version, iterations))
            cipher.doFinal(cipherText)
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("Nieprawidłowe hasło kopii lub uszkodzony plik.", error)
        } catch (error: GeneralSecurityException) {
            throw IllegalArgumentException("Nie udało się odszyfrować kopii.", error)
        } finally {
            keyBytes.fill(0)
        }

        return try {
            decodePayload(plainText)
        } finally {
            plainText.fill(0)
        }
    }

    private fun encodePayload(entries: List<VaultEntry>): ByteArray {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("service", entry.service)
                    .put("website", entry.website)
                    .put("username", entry.username)
                    .put("password", entry.password)
                    .put("notes", entry.notes)
                    .put("createdAt", entry.createdAt)
                    .put("updatedAt", entry.updatedAt),
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("entries", array)
            .toString()
            .encodeToByteArray()
    }

    private fun decodePayload(plainText: ByteArray): List<VaultEntry> {
        val payload = runCatching { JSONObject(plainText.decodeToString()) }
            .getOrElse { throw IllegalArgumentException("Uszkodzona zawartość kopii.") }
        require(payload.optInt("version", -1) == FORMAT_VERSION) { "Nieobsługiwana zawartość kopii." }

        val array = payload.optJSONArray("entries")
            ?: throw IllegalArgumentException("Kopia nie zawiera listy wpisów.")
        require(array.length() <= MAX_ENTRIES) { "Kopia zawiera zbyt wiele wpisów." }

        val ids = HashSet<String>(array.length())
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.getJSONObject(index)
                val entry = VaultEntry(
                    id = requiredText(value, "id", 128),
                    service = requiredText(value, "service", 512),
                    website = optionalText(value, "website", 2_048),
                    username = requiredText(value, "username", 1_024),
                    password = requiredText(value, "password", 4_096),
                    notes = optionalText(value, "notes", 32_768),
                    createdAt = validTimestamp(value, "createdAt"),
                    updatedAt = validTimestamp(value, "updatedAt"),
                )
                require(ids.add(entry.id)) { "Kopia zawiera zduplikowany identyfikator wpisu." }
                require(entry.updatedAt >= entry.createdAt) { "Kopia zawiera nieprawidłowe daty wpisu." }
                add(entry)
            }
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val characters = passphrase.toCharArray()
        val specification = PBEKeySpec(characters, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KEY_FACTORY).generateSecret(specification).encoded
        } finally {
            characters.fill('\u0000')
            specification.clearPassword()
        }
    }

    private fun associatedData(version: Int, iterations: Int): ByteArray =
        "$MAGIC|$version|$KDF_NAME|$iterations|$CIPHER_NAME".encodeToByteArray()

    private fun decodeBase64(envelope: JSONObject, name: String, expectedSize: Int?): ByteArray {
        val value = envelope.optString(name)
        require(value.isNotBlank()) { "Brak pola kopii: $name." }
        val decoded = runCatching { decoder.decode(value) }
            .getOrElse { throw IllegalArgumentException("Nieprawidłowe pole kopii: $name.") }
        if (expectedSize != null) require(decoded.size == expectedSize) { "Nieprawidłowe pole kopii: $name." }
        return decoded
    }

    private fun requiredText(value: JSONObject, name: String, maxLength: Int): String {
        val text = value.optString(name)
        require(text.isNotBlank() && text.length <= maxLength) { "Nieprawidłowe pole wpisu: $name." }
        return text
    }

    private fun optionalText(value: JSONObject, name: String, maxLength: Int): String {
        val text = value.optString(name)
        require(text.length <= maxLength) { "Pole wpisu jest zbyt długie: $name." }
        return text
    }

    private fun validTimestamp(value: JSONObject, name: String): Long {
        val timestamp = value.optLong(name, -1L)
        require(timestamp > 0L) { "Nieprawidłowa data wpisu: $name." }
        return timestamp
    }

    private fun validatePassphrase(passphrase: String) {
        require(passphrase.length in MIN_PASSPHRASE_LENGTH..MAX_PASSPHRASE_LENGTH) {
            "Hasło kopii musi mieć od $MIN_PASSPHRASE_LENGTH do $MAX_PASSPHRASE_LENGTH znaków."
        }
    }
}