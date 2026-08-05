package com.blackserv.passwdgen

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

    internal fun normalizedHost(value: String?): String? = HostNormalizer.normalize(value)

    private fun groupKey(entry: VaultEntry): String {
        normalizedHost(entry.website)?.let { return "host:$it" }
        val service = entry.service.trim().lowercase(Locale.ROOT)
        return "service:$service"
    }
}
