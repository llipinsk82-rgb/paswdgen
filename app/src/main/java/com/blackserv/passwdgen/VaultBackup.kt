package com.blackserv.passwdgen

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


internal class VaultBackupKeyMaterial(
    val salt: ByteArray,
    val iterations: Int,
    val keyBytes: ByteArray,
) : AutoCloseable {
    override fun close() {
        keyBytes.fill(0)
    }
}

internal object VaultBackupCodec {
    private val MAGIC = "PASSWDGEN-BACKUP".encodeToByteArray()
    internal const val MAGIC_SIZE = 16

    private const val FORMAT_VERSION = 1
    private const val PAYLOAD_VERSION = 1
    private const val KDF_ID_PBKDF2_SHA256 = 1
    private const val CIPHER_ID_AES_256_GCM = 1
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

    init {
        check(MAGIC.size == MAGIC_SIZE)
    }

    fun prepareKey(
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): VaultBackupKeyMaterial {
        validatePassphrase(passphrase)
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Nieprawidłowy parametr zabezpieczenia kopii."
        }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return VaultBackupKeyMaterial(
            salt = salt,
            iterations = iterations,
            keyBytes = deriveKey(passphrase, salt, iterations),
        )
    }

    fun encode(
        entries: List<VaultEntry>,
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        val material = prepareKey(passphrase, iterations)
        return try {
            encodeWithKey(entries, material)
        } finally {
            material.close()
        }
    }

    fun encodeWithKey(
        entries: List<VaultEntry>,
        material: VaultBackupKeyMaterial,
    ): ByteArray {
        require(entries.size <= MAX_ENTRIES) { "Kopia zawiera zbyt wiele wpisów." }
        require(material.iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Nieprawidłowy parametr zabezpieczenia kopii."
        }
        require(material.salt.size == SALT_BYTES) { "Nieprawidłowa sól klucza kopii." }
        require(material.keyBytes.size == KEY_BITS / 8) { "Nieprawidłowy klucz kopii." }

        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val aad = associatedData(FORMAT_VERSION, material.iterations)
        val plainText = encodePayload(entries)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(material.keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.updateAAD(aad)
            val cipherText = cipher.doFinal(plainText)

            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.write(MAGIC)
                    output.writeInt(FORMAT_VERSION)
                    output.writeInt(KDF_ID_PBKDF2_SHA256)
                    output.writeInt(material.iterations)
                    output.writeInt(material.salt.size)
                    output.write(material.salt)
                    output.writeInt(CIPHER_ID_AES_256_GCM)
                    output.writeInt(iv.size)
                    output.write(iv)
                    output.writeInt(cipherText.size)
                    output.write(cipherText)
                }
                buffer.toByteArray().also {
                    require(it.size <= MAX_BACKUP_BYTES) { "Kopia jest zbyt duża." }
                }
            }
        } finally {
            plainText.fill(0)
        }
    }

    fun decode(data: ByteArray, passphrase: String): List<VaultEntry> {
        validatePassphrase(passphrase)
        require(data.isNotEmpty() && data.size <= MAX_BACKUP_BYTES) { "Nieprawidłowy rozmiar kopii." }

        val envelope = try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                val magic = ByteArray(MAGIC_SIZE).also(input::readFully)
                require(magic.contentEquals(MAGIC)) { "Plik nie jest kopią PasswdGen." }

                val version = input.readInt()
                require(version == FORMAT_VERSION) { "Nieobsługiwana wersja kopii: $version." }
                require(input.readInt() == KDF_ID_PBKDF2_SHA256) { "Nieobsługiwany algorytm KDF." }

                val iterations = input.readInt()
                require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
                    "Nieprawidłowy parametr zabezpieczenia kopii."
                }

                val salt = input.readSizedBytes(expectedSize = SALT_BYTES, maxSize = SALT_BYTES)
                require(input.readInt() == CIPHER_ID_AES_256_GCM) { "Nieobsługiwany algorytm szyfrowania." }
                val iv = input.readSizedBytes(expectedSize = IV_BYTES, maxSize = IV_BYTES)
                val cipherText = input.readSizedBytes(expectedSize = null, maxSize = MAX_BACKUP_BYTES)
                require(cipherText.size >= 16) { "Uszkodzona zawartość kopii." }
                require(input.available() == 0) { "Kopia zawiera nieoczekiwane dane." }

                BackupEnvelope(version, iterations, salt, iv, cipherText)
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Plik nie jest prawidłową kopią PasswdGen.", error)
        }

        val keyBytes = deriveKey(passphrase, envelope.salt, envelope.iterations)
        val plainText = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, envelope.iv),
            )
            cipher.updateAAD(associatedData(envelope.version, envelope.iterations))
            cipher.doFinal(envelope.cipherText)
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

    private fun encodePayload(entries: List<VaultEntry>): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeInt(PAYLOAD_VERSION)
            output.writeLong(System.currentTimeMillis())
            output.writeInt(entries.size)
            entries.forEach { entry ->
                output.writeText(entry.id, 128)
                output.writeText(entry.service, 512)
                output.writeText(entry.website, 2_048)
                output.writeText(entry.username, 1_024)
                output.writeText(entry.password, 4_096)
                output.writeText(entry.notes, 32_768)
                output.writeLong(entry.createdAt)
                output.writeLong(entry.updatedAt)
            }
        }
        buffer.toByteArray()
    }

    private fun decodePayload(plainText: ByteArray): List<VaultEntry> {
        return try {
            DataInputStream(ByteArrayInputStream(plainText)).use { input ->
                require(input.readInt() == PAYLOAD_VERSION) { "Nieobsługiwana zawartość kopii." }
                require(input.readLong() > 0L) { "Nieprawidłowa data utworzenia kopii." }
                val count = input.readInt()
                require(count in 0..MAX_ENTRIES) { "Kopia zawiera zbyt wiele wpisów." }

                val ids = HashSet<String>(count)
                val entries = ArrayList<VaultEntry>(count)
                repeat(count) {
                    val entry = VaultEntry(
                        id = input.readText(128, required = true),
                        service = input.readText(512, required = true),
                        website = input.readText(2_048, required = false),
                        username = input.readText(1_024, required = true),
                        password = input.readText(4_096, required = true),
                        notes = input.readText(32_768, required = false),
                        createdAt = input.readLong(),
                        updatedAt = input.readLong(),
                    )
                    require(ids.add(entry.id)) { "Kopia zawiera zduplikowany identyfikator wpisu." }
                    require(entry.createdAt > 0L && entry.updatedAt >= entry.createdAt) {
                        "Kopia zawiera nieprawidłowe daty wpisu."
                    }
                    entries += entry
                }
                require(input.available() == 0) { "Kopia zawiera nieoczekiwane dane." }
                entries
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Uszkodzona zawartość kopii.", error)
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
        "PASSWDGEN-BACKUP|$version|$KDF_NAME|$iterations|$CIPHER_NAME".encodeToByteArray()

    private fun DataOutputStream.writeText(value: String, maxBytes: Int) {
        val bytes = value.encodeToByteArray()
        require(bytes.size <= maxBytes) { "Pole wpisu jest zbyt długie." }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(maxBytes: Int, required: Boolean): String {
        val bytes = readSizedBytes(expectedSize = null, maxSize = maxBytes)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val value = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        if (required) require(value.isNotBlank()) { "Kopia zawiera puste wymagane pole wpisu." }
        return value
    }

    private fun DataInputStream.readSizedBytes(expectedSize: Int?, maxSize: Int): ByteArray {
        val size = readInt()
        require(size >= 0 && size <= maxSize) { "Nieprawidłowa długość pola kopii." }
        if (expectedSize != null) require(size == expectedSize) { "Nieprawidłowa długość pola kopii." }
        return ByteArray(size).also(::readFully)
    }

    private fun validatePassphrase(passphrase: String) {
        require(passphrase.length in MIN_PASSPHRASE_LENGTH..MAX_PASSPHRASE_LENGTH) {
            "Hasło kopii musi mieć od $MIN_PASSPHRASE_LENGTH do $MAX_PASSPHRASE_LENGTH znaków."
        }
    }

    private data class BackupEnvelope(
        val version: Int,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val cipherText: ByteArray,
    )
}