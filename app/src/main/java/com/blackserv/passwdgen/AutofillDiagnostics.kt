package com.blackserv.passwdgen

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class AutofillDiagnostic(
    val timestampMillis: Long,
    val packageName: String,
    val windowCount: Int,
    val nodeCount: Int,
    val autofillIdCount: Int,
    val textCandidateCount: Int,
    val usernameDetected: Boolean,
    val passwordDetected: Boolean,
    val webDomainDetected: Boolean,
    val outcome: String,
) {
    fun asReport(): String = buildString {
        appendLine("Czas: ${formatTimestamp(timestampMillis)}")
        appendLine("Pakiet: ${packageName.ifBlank { "nieprzekazany" }}")
        appendLine("Okna: $windowCount")
        appendLine("Węzły: $nodeCount")
        appendLine("Pola z AutofillId: $autofillIdCount")
        appendLine("Kandydaci tekstowi: $textCandidateCount")
        appendLine("Login rozpoznany: ${yesNo(usernameDetected)}")
        appendLine("Hasło rozpoznane: ${yesNo(passwordDetected)}")
        appendLine("Domena WWW: ${yesNo(webDomainDetected)}")
        append("Wynik: $outcome")
    }

    companion object {
        private fun yesNo(value: Boolean): String = if (value) "tak" else "nie"

        private fun formatTimestamp(value: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
    }
}

internal object AutofillDiagnosticStore {
    private const val PREFS = "autofill_diagnostics"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_PACKAGE = "package"
    private const val KEY_WINDOWS = "windows"
    private const val KEY_NODES = "nodes"
    private const val KEY_IDS = "ids"
    private const val KEY_TEXT_CANDIDATES = "text_candidates"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_WEB_DOMAIN = "web_domain"
    private const val KEY_OUTCOME = "outcome"

    fun save(context: Context, value: AutofillDiagnostic) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TIMESTAMP, value.timestampMillis)
            .putString(KEY_PACKAGE, value.packageName)
            .putInt(KEY_WINDOWS, value.windowCount)
            .putInt(KEY_NODES, value.nodeCount)
            .putInt(KEY_IDS, value.autofillIdCount)
            .putInt(KEY_TEXT_CANDIDATES, value.textCandidateCount)
            .putBoolean(KEY_USERNAME, value.usernameDetected)
            .putBoolean(KEY_PASSWORD, value.passwordDetected)
            .putBoolean(KEY_WEB_DOMAIN, value.webDomainDetected)
            .putString(KEY_OUTCOME, value.outcome)
            .apply()
    }

    fun load(context: Context): AutofillDiagnostic? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timestamp = preferences.getLong(KEY_TIMESTAMP, 0L)
        if (timestamp <= 0L) return null
        return AutofillDiagnostic(
            timestampMillis = timestamp,
            packageName = preferences.getString(KEY_PACKAGE, "").orEmpty(),
            windowCount = preferences.getInt(KEY_WINDOWS, 0),
            nodeCount = preferences.getInt(KEY_NODES, 0),
            autofillIdCount = preferences.getInt(KEY_IDS, 0),
            textCandidateCount = preferences.getInt(KEY_TEXT_CANDIDATES, 0),
            usernameDetected = preferences.getBoolean(KEY_USERNAME, false),
            passwordDetected = preferences.getBoolean(KEY_PASSWORD, false),
            webDomainDetected = preferences.getBoolean(KEY_WEB_DOMAIN, false),
            outcome = preferences.getString(KEY_OUTCOME, "brak").orEmpty(),
        )
    }
}
