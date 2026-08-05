package com.blackserv.passwdgen

import android.app.assist.AssistStructure
import android.view.autofill.AutofillId
import java.util.Locale

internal object AutofillSpaSubmitPolicy {
    private val actionDescriptor = Regex(
        "(?iu)(^|[^\\p{L}])(submit|continue|next|login|log in|sign in|sign up|register|" +
            "create account|join|finish|complete|save|zaloguj|dalej|kontynuuj|zarejestruj|" +
            "utwórz konto|utworz konto|załóż konto|zaloz konto|przejdź dalej|przejdz dalej|" +
            "zapisz|potwierdź|potwierdz|gotowe)([^\\p{L}]|$)",
    )

    private val safeAttributeNames = setOf(
        "type",
        "role",
        "value",
        "aria-label",
        "name",
        "id",
        "title",
        "data-testid",
        "data-test",
        "data-qa",
    )

    fun find(structures: List<AssistStructure>): AutofillId? {
        structures.asReversed().forEach { structure ->
            for (windowIndex in 0 until structure.windowNodeCount) {
                var match: AutofillId? = null
                walk(structure.getWindowNodeAt(windowIndex).rootViewNode) { node ->
                    if (match != null) return@walk
                    val id = node.autofillId ?: return@walk
                    val htmlInfo = node.htmlInfo
                    val attributes = htmlInfo?.attributes.orEmpty()
                        .mapNotNull { attribute ->
                            val name = attribute.first?.lowercase(Locale.ROOT) ?: return@mapNotNull null
                            val value = attribute.second ?: return@mapNotNull null
                            if (name in safeAttributeNames) name to value else null
                        }
                    if (
                        isActionCandidate(
                            htmlTag = htmlInfo?.tag,
                            htmlAttributes = attributes,
                            className = node.className,
                            text = node.text,
                            contentDescription = node.contentDescription,
                            idEntry = node.idEntry,
                            clickable = node.isClickable,
                        )
                    ) {
                        match = id
                    }
                }
                if (match != null) return match
            }
        }
        return null
    }

    fun isActionCandidate(
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
        val interactive = clickable ||
            normalizedTag == "button" ||
            normalizedRole == "button" ||
            htmlType in setOf("button", "image")
        if (!interactive) return false

        val descriptor = buildList {
            add(text?.toString())
            add(contentDescription?.toString())
            add(idEntry)
            add(className?.toString())
            safeAttributeNames.forEach { name -> add(attributes[name]) }
        }.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
        return actionDescriptor.containsMatchIn(descriptor)
    }

    private fun walk(
        node: AssistStructure.ViewNode,
        visit: (AssistStructure.ViewNode) -> Unit,
    ) {
        visit(node)
        for (index in 0 until node.childCount) walk(node.getChildAt(index), visit)
    }

    private fun normalizeToken(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)
}
