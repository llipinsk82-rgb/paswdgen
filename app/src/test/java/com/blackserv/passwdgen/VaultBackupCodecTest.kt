package com.blackserv.passwdgen

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

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

        assertEquals(entries, decoded)
        assertTrue(encoded.decodeToString().contains("PASSWDGEN-BACKUP"))
        assertTrue(!encoded.decodeToString().contains("correct-horse-battery-staple"))
        assertTrue(!encoded.decodeToString().contains("user@example.com"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongPassphraseIsRejected() {
        val encoded = VaultBackupCodec.encode(entries, "very strong backup passphrase", 100_000)
        VaultBackupCodec.decode(encoded, "different strong passphrase")
    }

    @Test(expected = IllegalArgumentException::class)
    fun modifiedCiphertextIsRejected() {
        val encoded = VaultBackupCodec.encode(entries, "very strong backup passphrase", 100_000)
        val envelope = JSONObject(encoded.decodeToString())
        val cipherText = Base64.getDecoder().decode(envelope.getString("payload"))
        cipherText[cipherText.lastIndex] = (cipherText.last().toInt() xor 1).toByte()
        envelope.put("payload", Base64.getEncoder().encodeToString(cipherText))

        VaultBackupCodec.decode(envelope.toString().encodeToByteArray(), "very strong backup passphrase")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownFormatVersionIsRejectedBeforeDecryption() {
        val encoded = VaultBackupCodec.encode(entries, "very strong backup passphrase", 100_000)
        val envelope = JSONObject(encoded.decodeToString()).put("version", 99)

        VaultBackupCodec.decode(envelope.toString().encodeToByteArray(), "very strong backup passphrase")
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortBackupPassphraseIsRejected() {
        VaultBackupCodec.encode(entries, "too-short", 100_000)
    }
}