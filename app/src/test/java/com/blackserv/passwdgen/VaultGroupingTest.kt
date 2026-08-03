package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultGroupingTest {
    @Test
    fun `same normalized host is one group with multiple accounts`() {
        val groups = VaultGrouping.group(
            listOf(
                entry("A", "https://www.example.com/login", "second@example.com"),
                entry("B", "example.com/account", "first@example.com"),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals("example.com", groups.single().title)
        assertEquals(
            listOf("first@example.com", "second@example.com"),
            groups.single().entries.map(VaultEntry::username),
        )
    }

    @Test
    fun `different subdomains remain separate hosts`() {
        val groups = VaultGrouping.group(
            listOf(
                entry("Mail", "mail.example.com", "mail@example.com"),
                entry("Admin", "admin.example.com", "admin@example.com"),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(setOf("mail.example.com", "admin.example.com"), groups.map { it.title }.toSet())
    }

    @Test
    fun `entries without website group by service case insensitively`() {
        val groups = VaultGrouping.group(
            listOf(
                entry("Router", "", "admin"),
                entry(" router ", "", "operator"),
            ),
        )

        assertEquals(1, groups.size)
        assertNull(groups.single().host)
        assertEquals(2, groups.single().entries.size)
    }

    @Test
    fun `host normalization handles unicode and www`() {
        assertEquals("xn--d-uga.example", VaultGrouping.normalizedHost("https://www.dą.example/path"))
    }

    private fun entry(service: String, website: String, username: String) = VaultEntry(
        service = service,
        website = website,
        username = username,
        password = "test-password",
    )
}
