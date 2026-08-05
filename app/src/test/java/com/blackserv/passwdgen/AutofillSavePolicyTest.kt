package com.blackserv.passwdgen

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `password confirmation must match when present`() {
        assertTrue(
            AutofillPasswordConfirmationPolicy.isConsistent(
                password = "same-secret",
                confirmationPassword = "same-secret",
            ),
        )
        assertTrue(
            AutofillPasswordConfirmationPolicy.isConsistent(
                password = "same-secret",
                confirmationPassword = null,
            ),
        )
        assertFalse(
            AutofillPasswordConfirmationPolicy.isConsistent(
                password = "same-secret",
                confirmationPassword = "different-secret",
            ),
        )
    }

    @Test
    fun `save recovery selects unique email among registration fields`() {
        val candidates = listOf(
            AutofillUsernameCandidateSignals(
                descriptors = listOf("name firstName", "placeholder Imię"),
                inputType = InputType.TYPE_CLASS_TEXT,
                autofillHints = emptyList(),
                value = "Jan",
            ),
            AutofillUsernameCandidateSignals(
                descriptors = listOf("name lastName", "placeholder Nazwisko"),
                inputType = InputType.TYPE_CLASS_TEXT,
                autofillHints = emptyList(),
                value = "Kowalski",
            ),
            AutofillUsernameCandidateSignals(
                descriptors = listOf("field registration-3"),
                inputType = InputType.TYPE_CLASS_TEXT,
                autofillHints = emptyList(),
                value = "jan@example.com",
            ),
            AutofillUsernameCandidateSignals(
                descriptors = listOf("name address", "placeholder Adres"),
                inputType = InputType.TYPE_CLASS_TEXT,
                autofillHints = emptyList(),
                value = "Testowa 1",
            ),
        )

        assertEquals("jan@example.com", AutofillSaveUsernamePolicy.selectValue(candidates))
    }

    @Test
    fun `save recovery deduplicates the same email across contexts`() {
        val candidate = AutofillUsernameCandidateSignals(
            descriptors = listOf("field account"),
            inputType = InputType.TYPE_CLASS_TEXT,
            autofillHints = emptyList(),
            value = "member@example.com",
        )

        assertEquals(
            "member@example.com",
            AutofillSaveUsernamePolicy.selectValue(listOf(candidate, candidate.copy())),
        )
    }

    @Test
    fun `save recovery rejects ambiguous or non credential values`() {
        val firstEmail = AutofillUsernameCandidateSignals(
            descriptors = listOf("field one"),
            inputType = InputType.TYPE_CLASS_TEXT,
            autofillHints = emptyList(),
            value = "first@example.com",
        )
        val secondEmail = firstEmail.copy(value = "second@example.com")
        assertNull(AutofillSaveUsernamePolicy.selectValue(listOf(firstEmail, secondEmail)))

        val nameOnly = AutofillUsernameCandidateSignals(
            descriptors = listOf("name firstName", "placeholder Imię"),
            inputType = InputType.TYPE_CLASS_TEXT,
            autofillHints = emptyList(),
            value = "Jan",
        )
        assertNull(AutofillSaveUsernamePolicy.selectValue(listOf(nameOnly)))
    }
}
