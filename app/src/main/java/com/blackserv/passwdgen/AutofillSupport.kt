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
    val submitId: AutofillId?,
    val webDomain: String?,
    val packageName: String,
) {
    val targetLabel: String
        get() = webDomain ?: packageName
}

internal data class AutofillSessionForm(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val submitId: AutofillId?,
    val webDomain: String?,
    val packageName: String,
    val contextCount: Int,
) {
    val isComplete: Boolean
        get() = usernameId != null && passwordId != null
}

internal enum class AutofillSaveWorkflow {
    DELAY,
    COMPLETE,
}

internal object AutofillSessionPolicy {
    fun merge(forms: List<AutofillForm>, current: AutofillForm): AutofillSessionForm {
        val matching = forms.filter { form -> sameTarget(form, current) }
        val newestFirst = matching.asReversed()
        return AutofillSessionForm(
            usernameId = newestFirst.firstNotNullOfOrNull(AutofillForm::usernameId),
            passwordId = newestFirst.firstNotNullOfOrNull(AutofillForm::passwordId),
            submitId = current.submitId,
            webDomain = current.webDomain,
            packageName = current.packageName,
            contextCount = matching.size.coerceAtLeast(1),
        )
    }

    fun workflow(usernameDetected: Boolean, passwordDetected: Boolean): AutofillSaveWorkflow =
        if (usernameDetected && passwordDetected) {
            AutofillSaveWorkflow.COMPLETE
        } else {
            AutofillSaveWorkflow.DELAY
        }

    fun sameTarget(left: AutofillForm, right: AutofillForm): Boolean {
        val leftDomain = AutofillDomainPolicy.normalizeHost(left.webDomain)
        val rightDomain = AutofillDomainPolicy.normalizeHost(right.webDomain)
        if (leftDomain != null || rightDomain != null) {
            return leftDomain != null && leftDomain == rightDomain
        }
        return left.packageName.isNotBlank() && left.packageName == right.packageName
    }
}

internal data class AutofillParseStats(
    val packageName: String,
    val windowCount: Int,
    val nodeCount: Int,
    val autofillIdCount: Int,
    val textCandidateCount: Int,
    val usernameDetected: Boolean,
    val passwordDetected: Boolean,
    val webDomainDetected: Boolean,
)

internal data class AutofillParseResult(
    val form: AutofillForm?,
    val stats: AutofillParseStats,
)

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

internal object AutofillSubmitPolicy {
    private val actionDescriptor = Regex(
        "(^|[^a-z])(submit|continue|next|login|log in|sign in|sign up|register|create account|" +
            "zaloguj|dalej|kontynuuj|zarejestruj|utwórz konto)([^a-z]|$)",
    )

    fun isSubmitCandidate(
        htmlTag: String?,
        htmlAttributes: List<Pair<String, String>>,
        className: CharSequence?,
        text: CharSequence?,
        contentDescription: CharSequence?,
        idEntry: String?,
        clickable: Boolean,
    ): Boolean {
        val attributes = htmlAttributes.associate { (name, value) ->
            name.lowercase(Locale.ROOT) to value
        }
        val htmlType = normalizeToken(attributes["type"].orEmpty())
        if (htmlType == "submit") return true

        val normalizedTag = normalizeToken(htmlTag.orEmpty())
        val normalizedRole = normalizeToken(attributes["role"].orEmpty())
        val normalizedClass = className
            ?.toString()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val isButton = normalizedTag == "button" ||
            normalizedRole == "button" ||
            normalizedClass.endsWith("button") ||
            normalizedClass.contains(".button")
        if (!clickable || !isButton) return false

        val descriptor = buildList {
            add(text?.toString())
            add(contentDescription?.toString())
            add(idEntry)
            add(attributes["value"])
            add(attributes["aria-label"])
        }
            .filterNotNull()
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return actionDescriptor.containsMatchIn(descriptor)
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
        "role",
        "value",
    )

    fun parse(structure: AssistStructure): AutofillForm? = analyze(structure).form

    fun analyze(structure: AssistStructure): AutofillParseResult {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var submitId: AutofillId? = null
        var webDomain: String? = null
        var packageName: String? = structure.activityComponent
            ?.packageName
            ?.trim()
            ?.takeIf(String::isNotBlank)

        var nodeCount = 0
        var autofillIdCount = 0
        val genericTextFields = linkedSetOf<AutofillId>()

        for (windowIndex in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(windowIndex).rootViewNode
            packageName = packageName ?: root.idPackage?.trim()?.takeIf(String::isNotBlank)
            walk(root) { node ->
                nodeCount += 1
                packageName = packageName ?: node.idPackage?.trim()?.takeIf(String::isNotBlank)
                webDomain = webDomain ?: AutofillDomainPolicy.normalizeHost(node.webDomain)
                val id = node.autofillId ?: return@walk
                autofillIdCount += 1

                val htmlInfo = node.htmlInfo
                val htmlAttributes = htmlInfo
                    ?.attributes
                    .orEmpty()
                    .filter { attribute ->
                        attribute.first.lowercase(Locale.ROOT) in safeHtmlAttributes
                    }
                    .map { attribute -> attribute.first to attribute.second }
                if (
                    submitId == null &&
                    AutofillSubmitPolicy.isSubmitCandidate(
                        htmlTag = htmlInfo?.tag,
                        htmlAttributes = htmlAttributes,
                        className = node.className,
                        text = node.text,
                        contentDescription = node.contentDescription,
                        idEntry = node.idEntry,
                        clickable = node.isClickable,
                    )
                ) {
                    submitId = id
                }

                val htmlDescriptors = htmlAttributes.map { attribute ->
                    "${attribute.first} ${attribute.second}"
                }
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
                        if (isTextCandidate) genericTextFields += id
                    }
                }
            }
        }

        if (passwordId != null && usernameId == null && genericTextFields.size == 1) {
            usernameId = genericTextFields.single()
        }

        val resolvedPackage = packageName.orEmpty()
        val form = if (usernameId == null && passwordId == null) {
            null
        } else {
            AutofillForm(
                usernameId = usernameId,
                passwordId = passwordId,
                submitId = submitId,
                webDomain = webDomain,
                packageName = resolvedPackage,
            )
        }

        return AutofillParseResult(
            form = form,
            stats = AutofillParseStats(
                packageName = resolvedPackage,
                windowCount = structure.windowNodeCount,
                nodeCount = nodeCount,
                autofillIdCount = autofillIdCount,
                textCandidateCount = genericTextFields.size,
                usernameDetected = usernameId != null,
                passwordDetected = passwordId != null,
                webDomainDetected = webDomain != null,
            ),
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
