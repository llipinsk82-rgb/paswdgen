package com.blackserv.passwdgen

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal data class CsvCredential(
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val note: String,
)

internal data class PasswordCsvImportResult(
    val credentials: List<CsvCredential>,
    val totalRows: Int,
    val rejectedRows: Int,
    val duplicateRows: Int,
    val formulaLikeFields: Int,
)

internal data class PasswordCsvExportResult(
    val bytes: ByteArray,
    val exportedCount: Int,
    val skippedCount: Int,
    val formulaLikeFields: Int,
)

internal data class CsvImportPreview(
    val totalRows: Int,
    val readyRows: Int,
    val rejectedRows: Int,
    val duplicateRows: Int,
    val formulaLikeFields: Int,
)

internal object PasswordCsvCodec {
    internal const val MIME_TYPE = "text/csv"
    internal const val MAX_FILE_BYTES = 16 * 1024 * 1024
    internal const val MAX_IMPORT_ROWS = 10_000
    internal const val MAX_GOOGLE_EXPORT_ROWS = 3_000

    private const val MAX_NAME_BYTES = 512
    private const val MAX_URL_BYTES = 2_048
    private const val MAX_USERNAME_BYTES = 1_024
    private const val MAX_PASSWORD_BYTES = 4_096
    private const val MAX_NOTE_BYTES = 32_768

    fun decode(data: ByteArray): PasswordCsvImportResult {
        require(data.isNotEmpty() && data.size <= MAX_FILE_BYTES) { "Nieprawidłowy rozmiar pliku CSV." }
        val text = strictUtf8(data).removePrefix("\uFEFF")
        val rows = parseRows(text)
        require(rows.isNotEmpty()) { "Plik CSV jest pusty." }

        val header = rows.first().map(::normalizeHeader)
        val urlIndex = requiredHeaderIndex(header, "url")
        val usernameIndex = requiredHeaderIndex(header, "username")
        val passwordIndex = requiredHeaderIndex(header, "password")
        val nameIndex = optionalHeaderIndex(header, "name")
        val noteIndex = optionalHeaderIndex(header, "note")

        var totalRows = 0
        var rejectedRows = 0
        var duplicateRows = 0
        var formulaLikeFields = 0
        val seen = HashSet<String>()
        val accepted = ArrayList<CsvCredential>()

        rows.drop(1).forEach { row ->
            if (row.all(String::isBlank)) return@forEach
            totalRows += 1
            require(totalRows <= MAX_IMPORT_ROWS) {
                "Plik CSV zawiera więcej niż $MAX_IMPORT_ROWS rekordów. Podziel go na mniejsze pliki."
            }

            val credential = runCatching {
                val url = row.valueAt(urlIndex).trim()
                val username = row.valueAt(usernameIndex).trim()
                val password = row.valueAt(passwordIndex)
                val name = nameIndex?.let { index -> row.valueAt(index) }.orEmpty().trim()
                val note = noteIndex?.let { index -> row.valueAt(index) }.orEmpty()

                require(url.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                    "Brak wymaganego pola CSV."
                }
                validateField(name, MAX_NAME_BYTES)
                validateField(url, MAX_URL_BYTES)
                validateField(username, MAX_USERNAME_BYTES)
                validateField(password, MAX_PASSWORD_BYTES)
                validateField(note, MAX_NOTE_BYTES)

                CsvCredential(
                    name = name.ifBlank { serviceNameFromUrl(url) },
                    url = url,
                    username = username,
                    password = password,
                    note = note,
                )
            }.getOrElse {
                rejectedRows += 1
                return@forEach
            }

            formulaLikeFields += listOf(
                credential.name,
                credential.url,
                credential.username,
                credential.password,
                credential.note,
            ).count(::isFormulaLike)

            if (!seen.add(duplicateKey(credential.url, credential.username))) {
                duplicateRows += 1
            } else {
                accepted += credential
            }
        }

        return PasswordCsvImportResult(
            credentials = accepted,
            totalRows = totalRows,
            rejectedRows = rejectedRows,
            duplicateRows = duplicateRows,
            formulaLikeFields = formulaLikeFields,
        )
    }

    fun encode(entries: List<VaultEntry>): PasswordCsvExportResult {
        val exportable = entries.filter { it.website.isNotBlank() }
        val selected = exportable.take(MAX_GOOGLE_EXPORT_ROWS)
        val skipped = entries.size - selected.size
        var formulaLikeFields = 0

        val output = StringBuilder("name,url,username,password,note\r\n")
        selected.forEach { entry ->
            val values = listOf(
                entry.service,
                entry.website,
                entry.username,
                entry.password,
                entry.notes,
            )
            formulaLikeFields += values.count(::isFormulaLike)
            output.append(values.joinToString(",", transform = ::quote))
            output.append("\r\n")
        }

        return PasswordCsvExportResult(
            bytes = output.toString().encodeToByteArray(),
            exportedCount = selected.size,
            skippedCount = skipped,
            formulaLikeFields = formulaLikeFields,
        )
    }

    fun duplicateKey(url: String, username: String): String =
        "${normalizeUrlForDuplicate(url)}\u0000${username.trim().lowercase(Locale.ROOT)}"

    fun isFormulaLike(value: String): Boolean {
        val trimmed = value.trimStart()
        return trimmed.firstOrNull() in setOf('=', '+', '-', '@')
    }

    private fun strictUtf8(data: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(data)).toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("Plik CSV nie jest poprawnym tekstem UTF-8.", error)
        }
    }

    private fun parseRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun finishField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun finishRow() {
            finishField()
            rows.add(row)
            require(rows.size <= MAX_IMPORT_ROWS + 2) {
                "Plik CSV zawiera zbyt wiele wierszy."
            }
            row = ArrayList()
        }

        while (index < text.length) {
            val character = text[index]
            if (inQuotes) {
                when {
                    character == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        field.append('"')
                        index += 2
                    }
                    character == '"' -> {
                        inQuotes = false
                        index += 1
                    }
                    else -> {
                        field.append(character)
                        index += 1
                    }
                }
            } else {
                when (character) {
                    '"' -> {
                        require(field.isEmpty()) { "Nieprawidłowy cudzysłów w pliku CSV." }
                        inQuotes = true
                        index += 1
                    }
                    ',' -> {
                        finishField()
                        index += 1
                    }
                    '\r' -> {
                        finishRow()
                        index += if (index + 1 < text.length && text[index + 1] == '\n') 2 else 1
                    }
                    '\n' -> {
                        finishRow()
                        index += 1
                    }
                    else -> {
                        field.append(character)
                        index += 1
                    }
                }
            }
        }

        require(!inQuotes) { "Plik CSV zawiera niedomknięty cudzysłów." }
        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }

    private fun requiredHeaderIndex(header: List<String>, name: String): Int {
        val index = header.indexOf(name)
        require(index >= 0) { "Brak wymaganej kolumny CSV: $name." }
        return index
    }

    private fun optionalHeaderIndex(header: List<String>, name: String): Int? =
        header.indexOf(name).takeIf { it >= 0 }

    private fun normalizeHeader(value: String): String = value.trim().lowercase(Locale.ROOT)

    private fun List<String>.valueAt(index: Int): String = getOrElse(index) { "" }

    private fun validateField(value: String, maxBytes: Int) {
        require(value.encodeToByteArray().size <= maxBytes) { "Pole CSV jest zbyt długie." }
    }

    private fun serviceNameFromUrl(url: String): String {
        val candidate = if (url.contains("://")) url else "https://$url"
        val host = runCatching { URI(candidate).host.orEmpty() }.getOrDefault("")
            .lowercase(Locale.ROOT)
            .removePrefix("www.")
        val label = host.substringBefore('.').ifBlank { "Import CSV" }
        return label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun normalizeUrlForDuplicate(url: String): String =
        url.trim().lowercase(Locale.ROOT).trimEnd('/')

    private fun quote(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
