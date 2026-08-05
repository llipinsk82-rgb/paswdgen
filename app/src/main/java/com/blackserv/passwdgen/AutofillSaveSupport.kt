package com.blackserv.passwdgen

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import java.util.Locale
import java.util.UUID

internal sealed interface AutofillSaveTarget {
    val label: String

    data class Web(val domain: String) : AutofillSaveTarget {
        override val label: String = domain
    }

    data class Native(val identity: NativeAppIdentity) : AutofillSaveTarget {
        override val label: String = identity.appLabel
    }
}

internal data class PendingAutofillSave(
    val target: AutofillSaveTarget,
    val username: String,
    val password: String,
    val capturedAtMillis: Long = System.currentTimeMillis(),
)

internal data class CapturedAutofillCredential(
    val username: String,
    val password: String,
    val webDomain: String?,
    val packageName: String,
)

internal object AutofillPasswordConfirmationPolicy {
    fun isConsistent(password: String, confirmationPassword: String?): Boolean =
        confirmationPassword.isNullOrEmpty() || password == confirmationPassword
}

internal object AutofillSaveUsernamePolicy {
    fun selectValue(candidates: List<AutofillUsernameCandidateSignals>): String? {
        val unique = candidates
            .mapNotNull { candidate ->
                val value = candidate.value?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                candidate.copy(value = value)
            }
            .distinctBy { candidate -> candidate.value.orEmpty().lowercase(Locale.ROOT) }
        if (unique.isEmpty()) return null

        val scored = unique.mapIndexed { index, candidate ->
            index to AutofillUsernameFallbackPolicy.score(candidate)
        }
        val bestScore = scored.maxOf { it.second }
        if (bestScore < MIN_SAVE_USERNAME_SCORE) return null
        val bestIndex = scored.filter { it.second == bestScore }.singleOrNull()?.first ?: return null
        return unique[bestIndex].value
    }

    private const val MIN_SAVE_USERNAME_SCORE = 100
}

internal object AutofillSaveExtractor {
    private val safeHtmlAttributes = setOf(
        "autocomplete",
        "name",
        "id",
        "type",
        "placeholder",
        "aria-label",
        "inputmode",
    )

    fun extract(structures: List<AssistStructure>): CapturedAutofillCredential? {
        var username: String? = null
        var password: String? = null
        var confirmationPassword: String? = null
        var webDomain: String? = null
        var packageName: String? = null
        val fallbackUsernameCandidates = mutableListOf<AutofillUsernameCandidateSignals>()

        structures.asReversed().forEach { structure ->
            val form = AssistStructureParser.parse(structure)
            if (form != null) {
                webDomain = webDomain ?: form.webDomain
                packageName = packageName ?: form.packageName.takeIf(String::isNotBlank)
                username = username ?: findTextValue(structure, form.usernameId)
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                password = password ?: findTextValue(structure, form.passwordId)
                    ?.takeIf(String::isNotEmpty)
                confirmationPassword = confirmationPassword ?: findTextValue(
                    structure,
                    form.confirmationPasswordId,
                )?.takeIf(String::isNotEmpty)
            }
            if (username == null) {
                fallbackUsernameCandidates += collectUsernameCandidates(structure)
            }
        }

        username = username ?: AutofillSaveUsernamePolicy.selectValue(fallbackUsernameCandidates)
        val safeUsername = username?.takeIf { it.length <= MAX_USERNAME_LENGTH } ?: return null
        val safePassword = password?.takeIf { it.length <= MAX_PASSWORD_LENGTH } ?: return null
        val safeConfirmation = confirmationPassword
            ?.takeIf { it.length <= MAX_PASSWORD_LENGTH }
            ?: confirmationPassword?.let { return null }
        if (!AutofillPasswordConfirmationPolicy.isConsistent(safePassword, safeConfirmation)) {
            return null
        }

        return CapturedAutofillCredential(
            username = safeUsername,
            password = safePassword,
            webDomain = AutofillDomainPolicy.normalizeHost(webDomain),
            packageName = packageName.orEmpty(),
        )
    }

    private fun collectUsernameCandidates(
        structure: AssistStructure,
    ): List<AutofillUsernameCandidateSignals> = buildList {
        for (windowIndex in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(windowIndex).rootViewNode) { node ->
                val value = textValue(node)?.trim()?.takeIf(String::isNotBlank) ?: return@walk
                val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
                val textCandidate = node.autofillType == View.AUTOFILL_TYPE_TEXT ||
                    inputClass == InputType.TYPE_CLASS_TEXT ||
                    inputClass == InputType.TYPE_CLASS_NUMBER
                if (!textCandidate) return@walk

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
                if (kind == AutofillFieldKind.PASSWORD) return@walk

                val descriptors = buildList {
                    add(node.idEntry)
                    add(node.hint?.toString())
                    add(node.contentDescription?.toString())
                    addAll(htmlDescriptors)
                }.filterNotNull()
                add(
                    AutofillUsernameCandidateSignals(
                        descriptors = descriptors,
                        inputType = node.inputType,
                        autofillHints = node.autofillHints.orEmpty().toList(),
                        value = value,
                    ),
                )
            }
        }
    }

    private fun findTextValue(structure: AssistStructure, id: AutofillId?): String? {
        if (id == null) return null
        for (windowIndex in 0 until structure.windowNodeCount) {
            val value = findTextValue(structure.getWindowNodeAt(windowIndex).rootViewNode, id)
            if (value != null) return value
        }
        return null
    }

    private fun findTextValue(node: AssistStructure.ViewNode, id: AutofillId): String? {
        if (node.autofillId == id) return textValue(node)
        for (index in 0 until node.childCount) {
            findTextValue(node.getChildAt(index), id)?.let { return it }
        }
        return null
    }

    private fun textValue(node: AssistStructure.ViewNode): String? {
        val value = node.autofillValue
        return if (value != null && value.isText) value.textValue?.toString() else null
    }

    private fun walk(
        node: AssistStructure.ViewNode,
        visit: (AssistStructure.ViewNode) -> Unit,
    ) {
        visit(node)
        for (index in 0 until node.childCount) walk(node.getChildAt(index), visit)
    }

    private const val MAX_USERNAME_LENGTH = 512
    private const val MAX_PASSWORD_LENGTH = 8_192
}

internal object PendingAutofillSaveStore {
    private data class Item(
        val value: PendingAutofillSave,
        val expiresAtMillis: Long,
    )

    private val items = linkedMapOf<String, Item>()

    @Synchronized
    fun put(value: PendingAutofillSave): String {
        purgeExpired()
        while (items.size >= MAX_ITEMS) items.remove(items.keys.first())
        val token = UUID.randomUUID().toString()
        items[token] = Item(value, System.currentTimeMillis() + TTL_MILLIS)
        return token
    }

    @Synchronized
    fun peek(token: String): PendingAutofillSave? {
        purgeExpired()
        return items[token]?.value
    }

    @Synchronized
    fun remove(token: String) {
        items.remove(token)
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        items.entries.removeAll { it.value.expiresAtMillis <= now }
    }

    private const val MAX_ITEMS = 8
    private const val TTL_MILLIS = 2 * 60 * 1_000L
}

internal object AutofillSavePolicy {
    fun matchingEntries(
        entries: List<VaultEntry>,
        pending: PendingAutofillSave,
    ): List<VaultEntry> = entries.filter { entry ->
        entry.username == pending.username && when (val target = pending.target) {
            is AutofillSaveTarget.Web -> AutofillDomainPolicy.matches(entry.website, target.domain)
            is AutofillSaveTarget.Native -> entry.androidApps.any { binding ->
                NativeAppBindingPolicy.matches(binding, target.identity)
            }
        }
    }

    fun createEntry(pending: PendingAutofillSave): VaultEntry = when (val target = pending.target) {
        is AutofillSaveTarget.Web -> VaultEntry(
            service = target.domain,
            website = "https://${target.domain}",
            username = pending.username,
            password = pending.password,
        )

        is AutofillSaveTarget.Native -> VaultEntry(
            service = target.identity.appLabel,
            username = pending.username,
            password = pending.password,
            androidApps = listOf(target.identity.toBinding()),
        )
    }

    fun updateEntry(existing: VaultEntry, pending: PendingAutofillSave): VaultEntry {
        val bindings = when (val target = pending.target) {
            is AutofillSaveTarget.Web -> existing.androidApps
            is AutofillSaveTarget.Native -> existing.androidApps
                .filterNot { it.packageName == target.identity.packageName }
                .plus(target.identity.toBinding())
        }
        return existing.copy(
            password = pending.password,
            androidApps = bindings,
        )
    }

    private fun NativeAppIdentity.toBinding(): AndroidAppBinding = AndroidAppBinding(
        packageName = packageName,
        signerSha256 = signerSha256,
    )
}
