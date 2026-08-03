package com.blackserv.passwdgen

import java.net.IDN
import java.net.URI
import java.util.Locale

internal data class VaultHostGroup(
    val key: String,
    val title: String,
    val host: String?,
    val entries: List<VaultEntry>,
)

internal object VaultGrouping {
    fun group(entries: List<VaultEntry>): List<VaultHostGroup> = entries
        .groupBy(::groupKey)
        .map { (key, groupedEntries) ->
            val sorted = groupedEntries.sortedWith(
                compareBy<VaultEntry> { it.username.lowercase(Locale.ROOT) }
                    .thenByDescending(VaultEntry::updatedAt),
            )
            val host = normalizedHost(sorted.firstOrNull()?.website)
            VaultHostGroup(
                key = key,
                title = host ?: sorted.firstOrNull()?.service.orEmpty(),
                host = host,
                entries = sorted,
            )
        }
        .sortedWith(
            compareBy<VaultHostGroup> { it.title.lowercase(Locale.ROOT) }
                .thenBy(VaultHostGroup::key),
        )

    internal fun normalizedHost(value: String?): String? {
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
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun groupKey(entry: VaultEntry): String {
        normalizedHost(entry.website)?.let { return "host:$it" }
        val service = entry.service.trim().lowercase(Locale.ROOT)
        return "service:$service"
    }
}
