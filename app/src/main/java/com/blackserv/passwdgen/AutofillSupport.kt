package com.blackserv.passwdgen

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
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
        "userid",
        "login",
        "email",
        "emailaddress",
        "webusername",
    )
    private val passwordDescriptor =
        Regex("(^|[^a-z])(password|passwd|pwd|pass code|passcode)([^a-z]|$)")
    private val usernameDescriptor =
        Regex("(^|[^a-z])(username|user name|user id|userid|login|email|e-mail|email address|account)([^a-z]|$)")

    fun classify(
        autofillHints: Array<String>?,
        inputType: Int,
        idEntry: String?,
        hintText: CharSequence?,
        contentDescription: CharSequence? = null,
        additionalDescriptors: List<String> = emptyList(),
    ): AutofillFieldKind? {
        val normalizedHints = autofillHints.orEmpty().map(::normalizeToken)
        if (normalizedHints.any(passwordHints::contains)) return AutofillFieldKind.PASSWORD
        if (normalizedHints.any(usernameHints::contains)) return AutofillFieldKind.USERNAME

        when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> {
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

            InputType.TYPE_CLASS_NUMBER -> {
                if (inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                    return AutofillFieldKind.PASSWORD
                }
            }
        }

        val descriptor = buildList {
            add(idEntry)
            add(hintText?.toString())
            add(contentDescription?.toString())
            addAll(additionalDescriptors)
        }
            .filterNotNull()
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
    fun normalizeHost(value: String?): String? =
        HostNormalizer.normalize(value, requirePublicStyleHost = true)

    fun matches(savedWebsite: String, requestedDomain: String): Boolean {
        val saved = normalizeHost(savedWebsite) ?: return false
        val requested = normalizeHost(requestedDomain) ?: return false
        return saved == requested
    }
}

internal object AssistStructureParser {
    private val safeHtmlAttributes = setOf(
        "autocomplete",
        "name",
        "id",
        "type",
        "placeholder",
        "aria-label",
    )

    fun parse(structure: AssistStructure): AutofillForm? {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var webDomain: String? = null
        var packageName: String? = structure.activityComponent
            ?.packageName
            ?.trim()
            ?.takeIf(String::isNotBlank)

        val genericTextFields = mutableListOf<AutofillId>()

        for (windowIndex in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(windowIndex).rootViewNode
            packageName = packageName ?: root.idPackage?.trim()?.takeIf(String::isNotBlank)
            walk(root) { node ->
                packageName = packageName ?: node.idPackage?.trim()?.takeIf(String::isNotBlank)
                webDomain = webDomain ?: AutofillDomainPolicy.normalizeHost(node.webDomain)
                val id = node.autofillId ?: return@walk
                val htmlDescriptors = node.htmlInfo
                    ?.attributes
                    .orEmpty()
                    .filter { attribute ->
                        attribute.first.lowercase(Locale.ROOT) in safeHtmlAttributes
                    }
                    .map { attribute -> "${attribute.first} ${attribute.second}" }

                val kind = AutofillFieldPolicy.classify(
                    autofillHints = node.autofillHints,
                    inputType = node.inputType,
                    idEntry = node.idEntry,
                    hintText = node.hint,
                    contentDescription = node.contentDescription,
                    additionalDescriptors = htmlDescriptors,
                )
                when (kind) {
                    AutofillFieldKind.USERNAME -> usernameId = usernameId ?: id
                    AutofillFieldKind.PASSWORD -> passwordId = passwordId ?: id
                    null -> {
                        val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
                        val isTextCandidate = node.autofillType == View.AUTOFILL_TYPE_TEXT ||
                            inputClass == InputType.TYPE_CLASS_TEXT ||
                            inputClass == InputType.TYPE_CLASS_NUMBER
                        if (isTextCandidate && id !in genericTextFields) genericTextFields += id
                    }
                }
            }
        }

        // Część natywnych aplikacji oznacza tylko pole hasła. W takim przypadku najbliższe
        // pojedyncze pole tekstowe może bezpiecznie pełnić rolę loginu, ale nie zgadujemy,
        // gdy kandydatów jest więcej.
        if (passwordId != null && usernameId == null && genericTextFields.size == 1) {
            usernameId = genericTextFields.single()
        }

        if (usernameId == null && passwordId == null) return null
        return AutofillForm(
            usernameId = usernameId,
            passwordId = passwordId,
            webDomain = webDomain,
            packageName = packageName.orEmpty(),
        )
    }

    private fun walk(
        node: AssistStructure.ViewNode,
        visit: (AssistStructure.ViewNode) -> Unit,
    ) {
        visit(node)
        for (index in 0 until node.childCount) walk(node.getChildAt(index), visit)
    }
}
