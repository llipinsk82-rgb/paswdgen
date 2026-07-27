package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCsvCodecTest {
    @Test
    fun importsBomReorderedHeadersQuotesAndNewlines() {
        val csv = "\uFEFFpassword,note,url,name,username\r\n" +
            "\"p,ass\",\"line one\nline \"\"two\"\"\",https://accounts.google.com,Google,user@example.com\r\n"

        val result = PasswordCsvCodec.decode(csv.encodeToByteArray())

        assertEquals(1, result.totalRows)
        assertEquals(0, result.rejectedRows)
        assertEquals(0, result.duplicateRows)
        assertEquals(
            CsvCredential(
                name = "Google",
                url = "https://accounts.google.com",
                username = "user@example.com",
                password = "p,ass",
                note = "line one\nline \"two\"",
            ),
            result.credentials.single(),
        )
    }

    @Test
    fun rejectsInvalidRowsAndDeduplicatesWithinFile() {
        val csv = "name,url,username,password,note\n" +
            "Google,https://google.com,user@example.com,secret,one\n" +
            "Google,https://google.com/,USER@example.com,secret-two,two\n" +
            "Missing password,https://example.com,user@example.com,,bad\n"

        val result = PasswordCsvCodec.decode(csv.encodeToByteArray())

        assertEquals(3, result.totalRows)
        assertEquals(1, result.credentials.size)
        assertEquals(1, result.duplicateRows)
        assertEquals(1, result.rejectedRows)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingRequiredHeaderIsRejected() {
        PasswordCsvCodec.decode("url,username\nhttps://example.com,user".encodeToByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unterminatedQuotedFieldIsRejected() {
        PasswordCsvCodec.decode(
            "url,username,password\n\"https://example.com,user,secret".encodeToByteArray(),
        )
    }

    @Test
    fun exportUsesGoogleHeaderQuotesFieldsAndSkipsEntriesWithoutUrl() {
        val entries = listOf(
            VaultEntry(
                service = "Example, Inc.",
                website = "https://example.com",
                username = "user@example.com",
                password = "s\"ecret",
                notes = "line one\nline two",
            ),
            VaultEntry(
                service = "Local only",
                username = "local",
                password = "secret",
            ),
        )

        val result = PasswordCsvCodec.encode(entries)
        val csv = result.bytes.decodeToString()

        assertEquals(1, result.exportedCount)
        assertEquals(1, result.skippedCount)
        assertTrue(csv.startsWith("name,url,username,password,note\r\n"))
        assertTrue(csv.contains("\"Example, Inc.\""))
        assertTrue(csv.contains("\"s\"\"ecret\""))
        assertTrue(csv.contains("\"line one\nline two\""))
    }

    @Test
    fun formulaLikeFieldsAreReportedWithoutChangingCredentials() {
        val entry = VaultEntry(
            service = "=SUM(A1:A2)",
            website = "https://example.com",
            username = "+user",
            password = "@secret",
        )

        val export = PasswordCsvCodec.encode(listOf(entry))
        val imported = PasswordCsvCodec.decode(export.bytes)

        assertEquals(3, export.formulaLikeFields)
        assertEquals(3, imported.formulaLikeFields)
        assertEquals(entry.service, imported.credentials.single().name)
        assertEquals(entry.username, imported.credentials.single().username)
        assertEquals(entry.password, imported.credentials.single().password)
    }
}
