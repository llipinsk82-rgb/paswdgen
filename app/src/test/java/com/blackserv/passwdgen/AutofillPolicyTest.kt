package com.blackserv.passwdgen

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillPolicyTest {
    @Test
    fun `classifies standard autofill hints`() {
        assertEquals(
            AutofillFieldKind.USERNAME,
            AutofillFieldPolicy.classify(arrayOf("emailAddress"), 0, null, null),
        )
        assertEquals(
            AutofillFieldKind.PASSWORD,
            AutofillFieldPolicy.classify(arrayOf("password"), 0, null, null),
        )
    }

    @Test
    fun `classifies input types and conservative descriptors`() {
        assertEquals(
            AutofillFieldKind.PASSWORD,
            AutofillFieldPolicy.classify(
                null,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                "account_pwd",
                null,
            ),
        )
        assertEquals(
            AutofillFieldKind.USERNAME,
            AutofillFieldPolicy.classify(null, 0, "login_email", "E-mail"),
        )
        assertNull(AutofillFieldPolicy.classify(null, 0, "search", "Szukaj"))
    }

    @Test
    fun `classifies accessibility and safe html metadata`() {
        assertEquals(
            AutofillFieldKind.USERNAME,
            AutofillFieldPolicy.classify(
                autofillHints = null,
                inputType = 0,
                idEntry = null,
                hintText = null,
                contentDescription = "Email address",
            ),
        )
        assertEquals(
            AutofillFieldKind.PASSWORD,
            AutofillFieldPolicy.classify(
                autofillHints = null,
                inputType = 0,
                idEntry = null,
                hintText = null,
                additionalDescriptors = listOf("placeholder Password", "name accountPassword"),
            ),
        )
        assertNull(
            AutofillFieldPolicy.classify(
                autofillHints = null,
                inputType = InputType.TYPE_CLASS_TEXT,
                idEntry = null,
                hintText = null,
                contentDescription = "Search parking locations",
                additionalDescriptors = listOf("placeholder Search"),
            ),
        )
    }

    @Test
    fun `autofill only matches exact normalized host`() {
        assertTrue(AutofillDomainPolicy.matches("https://www.example.com/login", "example.com"))
        assertFalse(AutofillDomainPolicy.matches("login.example.com", "example.com"))
        assertFalse(AutofillDomainPolicy.matches("example.com", "evil-example.com"))
        assertFalse(AutofillDomainPolicy.matches("example.com", "sub.example.com"))
    }

    @Test
    fun `native app binding requires exact package and signer set`() {
        val signer = "a".repeat(64)
        val binding = AndroidAppBinding(
            packageName = "com.example.shop",
            signerSha256 = signer,
        )

        assertTrue(
            NativeAppBindingPolicy.matches(
                binding,
                NativeAppIdentity("com.example.shop", "Example Shop", signer),
            ),
        )
        assertFalse(
            NativeAppBindingPolicy.matches(
                binding,
                NativeAppIdentity("com.example.shop", "Example Shop", "b".repeat(64)),
            ),
        )
        assertFalse(
            NativeAppBindingPolicy.matches(
                binding,
                NativeAppIdentity("com.example.fake", "Fake Shop", signer),
            ),
        )
    }
}
