package com.blackserv.passwdgen

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.autofill.AutofillId
import java.net.IDN
import java.net.URI
import java.util.Locale

internal enum class AutofillFieldKind {
    USERNAME,
    PASSWORD,
}

internal data class AutofillForm(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val webDomain: String?,
    val packageName: String,
) {
    val targetLabel: String
        get() = webDomain ?: packageName
}

internal object AutofillFieldPolicy {
    private val passwordHints = setOf(
        "password",
        "currentpassword",
        "newpassword",
        "webpassword",
    )
    private val usernameHints = setOf(
        "username",
        "user",
        "login",
        "email",
        "emailaddress",
        "webusername",
    )
    private val passwordDescriptor = Regex("(^|[^a-z])(password|passwd|pwd)([^a-z]|$)")
    private val usernameDescriptor = Regex("(^|[^a-z])(username|user name|login|email|e-mail)([^a-z]|$)")

    fun classify(
        autofillHints: Array<String>?,
        inputType: Int,
        idEntry: String?,
        hintText: CharSequence?,
    ): AutofillFieldKind? {
        val normalizedHints = autofillHints.orEmpty().map(::normalizeToken)
        if (normalizedHints.any(passwordHints::contains)) return AutofillFieldKind.PASSWORD
        if (normalizedHints.any(usernameHints::contains)) return AutofillFieldKind.USERNAME

        if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
            when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                -> return AutofillFieldKind.PASSWORD

                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                -> return AutofillFieldKind.USERNAME
            }
        }

        val descriptor = listOfNotNull(idEntry, hintText?.toString())
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        if (passwordDescriptor.containsMatchIn(descriptor)) return AutofillFieldKind.PASSWORD
        if (usernameDescriptor.containsMatchIn(descriptor)) return AutofillFieldKind.USERNAME
        return null
    }

    private fun normalizeToken(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)
}

internal object AutofillDomainPolicy {
    fun normalizeHost(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim()
        val host = runCatching {
            val candidate = if ("://" in raw) raw else "https://$raw"
            URI(candidate).host
        }.getOrNull() ?: raw.substringBefore('/').substringBefore(':')

        return runCatching {
            IDN.toASCII(host.trim().trim('.'))
                .lowercase(Locale.ROOT)
                .removePrefix("www.")
                .takeIf { it.isNotBlank() && '.' in it }
        }.getOrNull()
    }

    fun matches(savedWebsite: String, requestedDomain: String): Boolean {
        val saved = normalizeHost(savedWebsite) ?: return false
        val requested = normalizeHost(requestedDomain) ?: return false
        return saved == requested || saved.endsWith(".$requested") || requested.endsWith(".$saved")
    }
}

internal object AssistStructureParser {
    fun parse(structure: AssistStructure): AutofillForm? {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var webDomain: String? = null
        var packageName: String? = null

        for (windowIndex in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(windowIndex).rootViewNode
            packageName = packageName ?: root.idPackage
            walk(root) { node ->
                webDomain = webDomain ?: AutofillDomainPolicy.normalizeHost(node.webDomain)
                val id = node.autofillId ?: return@walk
                when (
                    AutofillFieldPolicy.classify(
                        autofillHints = node.autofillHints,
                        inputType = node.inputType,
                        idEntry = node.idEntry,
                        hintText = node.hint,
                    )
                ) {
                    AutofillFieldKind.USERNAME -> usernameId = usernameId ?: id
                    AutofillFieldKind.PASSWORD -> passwordId = passwordId ?: id
                    null -> Unit
                }
            }
        }

        if (usernameId == null && passwordId == null) return null
        return AutofillForm(
            usernameId = usernameId,
            passwordId = passwordId,
            webDomain = webDomain,
            packageName = packageName.orEmpty(),
        )
    }

    private inline fun walk(
        node: AssistStructure.ViewNode,
        visit: (AssistStructure.ViewNode) -> Unit,
    ) {
        visit(node)
        for (index in 0 until node.childCount) walk(node.getChildAt(index), visit)
    }
}
