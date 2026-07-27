package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class VaultBackupCodecTest {
    private val entries = listOf(
        VaultEntry(
            id = "11111111-1111-1111-1111-111111111111",
            service = "Google",
            website = "accounts.google.com",
            username = "user@example.com",
            password = "correct-horse-battery-staple!",
            notes = "konto główne",
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000,
        ),
        VaultEntry(
            id = "22222222-2222-2222-2222-222222222222",
            service = "GitHub",
            website = "github.com",
            username = "octocat",
            password = "another-long-secret-password",
            createdAt = 1_700_000_200_000,
            updatedAt = 1_700_000_300_000,
        ),
    )

    @Test
    fun encryptedBackupRoundTripsAllFields() {
        val encoded = VaultBackupCodec.encode(
            entries = entries,
            passphrase = "very strong backup passphrase",
            iterations = 100_000,
        )

        val decoded = VaultBackupCodec.decode(encoded, "very strong backup passphrase")
        val printable = String(encoded, StandardCharsets.ISO_8859_1)

        assertEquals(entries, decoded)
        assertTrue(printable.startsWith("PASSWDGEN-BACKUP"))
        assertTrue(!printable.contains("correct-horse-battery-staple"))
        assertTrue(!printable.contains("user@example.com"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongPassphraseIsRejected() {
        val encoded = VaultBackupCodec.encode(entries, "very strong backup passphrase", 100_000)
        VaultBackupCodec.decode(encoded, "different strong passphrase")
    }

    @Test(expected = IllegalArgumentException::class)
    fun modifiedCiphertextIsRejected() {
        val encoded = VaultBackupCodec.encode(entries, "very strong backup passphrase", 100_000)
        val modified = encoded.copyOf()
        modified[modified.lastIndex] = (modified.last().toInt() xor 1).toByte()

        VaultBackupCodec.decode(modified, "very strong backup passphrase")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownFormatVersionIsRejectedBeforeDecryption() {
        val encoded = VaultBackupCodec.encode(entries, "very strong backup passphrase", 100_000)
        val modified = encoded.copyOf()
        ByteBuffer.wrap(modified, VaultBackupCodec.MAGIC_SIZE, Int.SIZE_BYTES).putInt(99)

        VaultBackupCodec.decode(modified, "very strong backup passphrase")
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortBackupPassphraseIsRejected() {
        VaultBackupCodec.encode(entries, "too-short", 100_000)
    }
    @Test
    fun preparedKeyProducesPortableSnapshotsWithFreshIvs() {
        val passphrase = "very strong backup passphrase"
        val material = VaultBackupCodec.prepareKey(passphrase, 100_000)
        try {
            val first = VaultBackupCodec.encodeWithKey(entries, material)
            val second = VaultBackupCodec.encodeWithKey(entries, material)

            assertFalse(first.contentEquals(second))
            assertEquals(entries, VaultBackupCodec.decode(first, passphrase))
            assertEquals(entries, VaultBackupCodec.decode(second, passphrase))
        } finally {
            material.close()
        }
    }

}