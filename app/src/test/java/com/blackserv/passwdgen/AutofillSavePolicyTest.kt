package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillSavePolicyTest {
    @Test
    fun `web save updates only exact host and username`() {
        val existing = VaultEntry(
            service = "Example",
            website = "https://example.com/login",
            username = "user@example.com",
            password = "old-password",
        )
        val pending = PendingAutofillSave(
            target = AutofillSaveTarget.Web("example.com"),
            username = "user@example.com",
            password = "new-password",
        )

        assertEquals(
            listOf(existing),
            AutofillSavePolicy.matchingEntries(listOf(existing), pending),
        )
        assertEquals(
            "new-password",
            AutofillSavePolicy.updateEntry(existing, pending).password,
        )

        val otherHost = pending.copy(target = AutofillSaveTarget.Web("login.example.com"))
        assertTrue(AutofillSavePolicy.matchingEntries(listOf(existing), otherHost).isEmpty())
    }

    @Test
    fun `native save requires exact package signer and username`() {
        val identity = NativeAppIdentity(
            packageName = "com.example.app",
            appLabel = "Example App",
            signerSha256 = "a".repeat(64),
        )
        val existing = VaultEntry(
            service = "Example App",
            username = "member",
            password = "old-password",
            androidApps = listOf(
                AndroidAppBinding(identity.packageName, identity.signerSha256),
            ),
        )
        val pending = PendingAutofillSave(
            target = AutofillSaveTarget.Native(identity),
            username = "member",
            password = "new-password",
        )

        assertEquals(
            listOf(existing),
            AutofillSavePolicy.matchingEntries(listOf(existing), pending),
        )

        val changedSigner = pending.copy(
            target = AutofillSaveTarget.Native(identity.copy(signerSha256 = "b".repeat(64))),
        )
        assertTrue(AutofillSavePolicy.matchingEntries(listOf(existing), changedSigner).isEmpty())
    }

    @Test
    fun `new web and native saves create correctly scoped entries`() {
        val web = AutofillSavePolicy.createEntry(
            PendingAutofillSave(
                target = AutofillSaveTarget.Web("example.com"),
                username = "web-user",
                password = "secret",
            ),
        )
        assertEquals("https://example.com", web.website)
        assertTrue(web.androidApps.isEmpty())

        val identity = NativeAppIdentity(
            packageName = "com.example.app",
            appLabel = "Example App",
            signerSha256 = "c".repeat(64),
        )
        val native = AutofillSavePolicy.createEntry(
            PendingAutofillSave(
                target = AutofillSaveTarget.Native(identity),
                username = "native-user",
                password = "secret",
            ),
        )
        assertEquals("Example App", native.service)
        assertEquals(
            listOf(AndroidAppBinding(identity.packageName, identity.signerSha256)),
            native.androidApps,
        )
    }
}
