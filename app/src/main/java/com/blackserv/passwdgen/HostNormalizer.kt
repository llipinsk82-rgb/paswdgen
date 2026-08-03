package com.blackserv.passwdgen

import java.net.IDN
import java.net.URI
import java.util.Locale

internal object HostNormalizer {
    fun normalize(value: String?, requirePublicStyleHost: Boolean = false): String? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim()
        val candidate = if ("://" in raw) raw else "https://$raw"
        val parsedHost = runCatching { URI(candidate).host }.getOrNull()
        val fallbackHost = raw
            .substringAfter("://", raw)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .let { authority ->
                if (authority.startsWith('[')) {
                    authority.substringAfter('[').substringBefore(']')
                } else {
                    authority.substringBefore(':')
                }
            }
        val host = parsedHost ?: fallbackHost

        return runCatching {
            IDN.toASCII(host.trim().trim('.'))
                .lowercase(Locale.ROOT)
                .removePrefix("www.")
                .takeIf {
                    it.isNotBlank() &&
                        it.none(Char::isWhitespace) &&
                        (!requirePublicStyleHost || '.' in it)
                }
        }.getOrNull()
    }
}
