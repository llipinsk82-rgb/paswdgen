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
    val confirmationPasswordId: AutofillId?,
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
    val confirmationPasswordId: AutofillId?,
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
            confirmationPasswordId = newestFirst.firstNotNullOfOrNull(
                AutofillForm::confirmationPasswordId,
            ),
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

internal data class AutofillUsernameCandidateSignals(
    val descriptors: List<String>,
    val inputType: Int,
    val autofillHints: List<String>,
    val value: String?,
)

internal object AutofillUsernameFallbackPolicy {
    private val usernameDescriptor = Regex(
        "(?iu)(^|[^\\p{L}])(username|user name|user id|userid|login|email|e-mail|" +
            "email address|account|customer number|member number)([^\\p{L}]|$)",
    )
    private val phoneDescriptor = Regex(
        "(?iu)(^|[^\\p{L}])(phone|mobile|telephone|tel|telefon|komórkowy|komorkowy)([^\\p{L}]|$)",
    )
    private val excludedDescriptor = Regex(
        "(?iu)(^|[^\\p{L}])(first name|firstname|given name|last name|lastname|surname|" +
            "family name|imię|imie|nazwisko|search|szukaj|address|adres|street|ulica|city|" +
            "miasto|postcode|postal|zip|kod pocztowy|company|firma|promo|coupon|newsletter)([^\\p{L}]|$)",
    )
    private val emailValue = Regex("^[^\\s@]{1,128}@[^\\s@]{1,255}\\.[^\\s@]{2,63}$")
    private val phoneValue = Regex("^\\+?[0-9][0-9 ()-]{5,23}[0-9]$")
    private val usernameHints = setOf(
        "username",
        "user",
        "userid",
        "login",
        "email",
        "emailaddress",
        "webusername",
    )
    private val phoneHints = setOf("phone", "phonenumber", "tel", "telephone", "mobile")

    fun selectIndex(candidates: List<AutofillUsernameCandidateSignals>): Int? {
        if (candidates.isEmpty()) return null
        val scored = candidates.mapIndexed { index, candidate -> index to score(candidate) }
        if (candidates.size == 1) {
            return scored.single().takeIf { it.second > BLOCKED_SCORE }?.first
        }

        val bestScore = scored.maxOf { it.second }
        if (bestScore < MULTI_CANDIDATE_MIN_SCORE) return null
        val best = scored.filter { it.second == bestScore }
        return best.singleOrNull()?.first
    }

    fun score(candidate: AutofillUsernameCandidateSignals): Int {
        val descriptor = candidate.descriptors
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        if (excludedDescriptor.containsMatchIn(descriptor)) return BLOCKED_SCORE

        val normalizedHints = candidate.autofillHints.map(::normalizeToken)
        val trimmedValue = candidate.value?.trim().orEmpty()
        var score = 0
        if (emailValue.matches(trimmedValue)) score += 220
        if (usernameDescriptor.containsMatchIn(descriptor)) score += 180
        if (normalizedHints.any(usernameHints::contains)) score += 180

        val inputClass = candidate.inputType and InputType.TYPE_MASK_CLASS
        val variation = candidate.inputType and InputType.TYPE_MASK_VARIATION
        if (
            inputClass == InputType.TYPE_CLASS_TEXT &&
            variation in setOf(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            )
        ) {
            score += 170
        }

        if (
            phoneValue.matches(trimmedValue) &&
            phoneDescriptor.containsMatchIn(descriptor)
        ) {
            score += 140
        }
        if (normalizedHints.any(phoneHints::contains)) score += 130
        if (trimmedValue.isNotEmpty()) score += 10
        return score
    }

    private fun normalizeToken(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    private const val BLOCKED_SCORE = -10_000
    private const val MULTI_CANDIDATE_MIN_SCORE = 100
}

internal data class AutofillPasswordCandidateSignals(
    val descriptors: List<String>,
    val autofillHints: List<String>,
    val value: String?,
)

internal object AutofillPasswordCandidatePolicy {
    private val confirmationDescriptor = Regex(
        "(?iu)(^|[^\\p{L}])(confirm|confirmation|repeat|retype|re-enter|verify|match|" +
            "powtórz|powtorz|potwierdź|potwierdz|potwierdzenie)([^\\p{L}]|$)",
    )
    private val newPasswordDescriptor = Regex(
        "(?iu)(^|[^\\p{L}])(new password|nowe hasło|nowe haslo)([^\\p{L}]|$)",
    )

    fun primaryIndex(candidates: List<AutofillPasswordCandidateSignals>): Int? {
        if (candidates.isEmpty()) return null
        return candidates.indices.maxByOrNull { index -> primaryScore(candidates[index]) }
    }

    fun confirmationIndex(
        candidates: List<AutofillPasswordCandidateSignals>,
        primaryIndex: Int?,
    ): Int? {
        if (primaryIndex == null) return null
        candidates.indices.firstOrNull { index ->
            index != primaryIndex && isConfirmation(candidates[index])
        }?.let { return it }

        val primaryValue = candidates[primaryIndex].value
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val matching = candidates.indices.filter { index ->
            index != primaryIndex && candidates[index].value == primaryValue
        }
        return matching.singleOrNull()
    }

    fun isConfirmation(candidate: AutofillPasswordCandidateSignals): Boolean {
        val descriptor = candidate.descriptors
            .plus(candidate.autofillHints)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return confirmationDescriptor.containsMatchIn(descriptor)
    }

    private fun primaryScore(candidate: AutofillPasswordCandidateSignals): Int {
        if (isConfirmation(candidate)) return -1_000
        val descriptor = candidate.descriptors
            .plus(candidate.autofillHints)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        var score = 10
        if (newPasswordDescriptor.containsMatchIn(descriptor)) score += 100
        if (candidate.autofillHints.any { normalizeToken(it) == "newpassword" }) score += 120
        return score
    }

    private fun normalizeToken(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)
}

internal object AutofillSubmitPolicy {
    private val actionDescriptor = Regex(
        "(?iu)(^|[^\\p{L}])(submit|continue|next|login|log in|sign in|sign up|register|" +
            "create account|join|finish|complete|save|zaloguj|dalej|kontynuuj|zarejestruj|" +
            "utwórz konto|utworz konto|załóż konto|zaloz konto|przejdź dalej|przejdz dalej|" +
            "zapisz|potwierdź|potwierdz|gotowe)([^\\p{L}]|$)",
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
        val hasSubmitMeaning = actionDescriptor.containsMatchIn(descriptor)
        return hasSubmitMeaning && (isButton || clickable)
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
    private data class ParsedTextCandidate(
        val id: AutofillId,
        val signals: AutofillUsernameCandidateSignals,
    )

    private data class ParsedPasswordCandidate(
        val id: AutofillId,
        val signals: AutofillPasswordCandidateSignals,
    )

    private val safeHtmlAttributes = setOf(
        "autocomplete",
        "name",
        "id",
        "type",
        "placeholder",
        "aria-label",
        "role",
        "value",
        "inputmode",
    )

    fun parse(structure: AssistStructure): AutofillForm? = analyze(structure).form

    fun analyze(structure: AssistStructure): AutofillParseResult {
        var usernameId: AutofillId? = null
        var submitId: AutofillId? = null
        var webDomain: String? = null
        var packageName: String? = structure.activityComponent
            ?.packageName
            ?.trim()
            ?.takeIf(String::isNotBlank)

        var nodeCount = 0
        var autofillIdCount = 0
        val genericTextFields = mutableListOf<ParsedTextCandidate>()
        val passwordFields = mutableListOf<ParsedPasswordCandidate>()

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
                val descriptors = buildList {
                    add(node.idEntry)
                    add(node.hint?.toString())
                    add(node.contentDescription?.toString())
                    addAll(htmlDescriptors)
                }.filterNotNull()
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
                    AutofillFieldKind.PASSWORD -> passwordFields += ParsedPasswordCandidate(
                        id = id,
                        signals = AutofillPasswordCandidateSignals(
                            descriptors = descriptors,
                            autofillHints = node.autofillHints.orEmpty().toList(),
                            value = currentTextValue(node),
                        ),
                    )

                    null -> {
                        val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
                        val isTextCandidate = node.autofillType == View.AUTOFILL_TYPE_TEXT ||
                            inputClass == InputType.TYPE_CLASS_TEXT ||
                            inputClass == InputType.TYPE_CLASS_NUMBER
                        if (isTextCandidate) {
                            genericTextFields += ParsedTextCandidate(
                                id = id,
                                signals = AutofillUsernameCandidateSignals(
                                    descriptors = descriptors,
                                    inputType = node.inputType,
                                    autofillHints = node.autofillHints.orEmpty().toList(),
                                    value = currentTextValue(node),
                                ),
                            )
                        }
                    }
                }
            }
        }

        val primaryPasswordIndex = AutofillPasswordCandidatePolicy.primaryIndex(
            passwordFields.map(ParsedPasswordCandidate::signals),
        )
        val confirmationPasswordIndex = AutofillPasswordCandidatePolicy.confirmationIndex(
            candidates = passwordFields.map(ParsedPasswordCandidate::signals),
            primaryIndex = primaryPasswordIndex,
        )
        val passwordId = primaryPasswordIndex?.let { passwordFields[it].id }
        val confirmationPasswordId = confirmationPasswordIndex?.let { passwordFields[it].id }

        if (usernameId == null) {
            val fallbackIndex = AutofillUsernameFallbackPolicy.selectIndex(
                genericTextFields.map(ParsedTextCandidate::signals),
            )
            usernameId = fallbackIndex?.let { genericTextFields[it].id }
        }

        val resolvedPackage = packageName.orEmpty()
        val form = if (usernameId == null && passwordId == null) {
            null
        } else {
            AutofillForm(
                usernameId = usernameId,
                passwordId = passwordId,
                confirmationPasswordId = confirmationPasswordId,
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

    private fun currentTextValue(node: AssistStructure.ViewNode): String? {
        val value = node.autofillValue
        if (value != null && value.isText) return value.textValue?.toString()
        return node.text?.toString()
    }

    private fun walk(
        node: AssistStructure.ViewNode,
        visit: (AssistStructure.ViewNode) -> Unit,
    ) {
        visit(node)
        for (index in 0 until node.childCount) walk(node.getChildAt(index), visit)
    }
}
