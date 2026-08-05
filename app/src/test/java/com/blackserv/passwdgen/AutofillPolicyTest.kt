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
    fun `detects explicit html submit controls`() {
        assertTrue(
            AutofillSubmitPolicy.isSubmitCandidate(
                htmlTag = "input",
                htmlAttributes = listOf("type" to "submit", "value" to "Create account"),
                className = "android.view.View",
                text = null,
                contentDescription = null,
                idEntry = "register",
                clickable = true,
            ),
        )
        assertTrue(
            AutofillSubmitPolicy.isSubmitCandidate(
                htmlTag = "button",
                htmlAttributes = listOf("role" to "button"),
                className = "android.widget.Button",
                text = "Sign up",
                contentDescription = null,
                idEntry = null,
                clickable = true,
            ),
        )
    }

    @Test
    fun `does not treat credential text fields or unrelated buttons as submit controls`() {
        assertFalse(
            AutofillSubmitPolicy.isSubmitCandidate(
                htmlTag = "input",
                htmlAttributes = listOf("type" to "text"),
                className = "android.widget.EditText",
                text = null,
                contentDescription = null,
                idEntry = "login",
                clickable = true,
            ),
        )
        assertFalse(
            AutofillSubmitPolicy.isSubmitCandidate(
                htmlTag = "button",
                htmlAttributes = emptyList(),
                className = "android.widget.Button",
                text = "Cancel",
                contentDescription = null,
                idEntry = null,
                clickable = true,
            ),
        )
    }

    @Test
    fun `multi-step save is delayed until login and password are both available`() {
        assertEquals(
            AutofillSaveWorkflow.DELAY,
            AutofillSessionPolicy.workflow(usernameDetected = true, passwordDetected = false),
        )
        assertEquals(
            AutofillSaveWorkflow.DELAY,
            AutofillSessionPolicy.workflow(usernameDetected = false, passwordDetected = true),
        )
        assertEquals(
            AutofillSaveWorkflow.COMPLETE,
            AutofillSessionPolicy.workflow(usernameDetected = true, passwordDetected = true),
        )
    }

    @Test
    fun `session contexts are combined only for the exact same web target`() {
        val loginStage = AutofillForm(
            usernameId = null,
            passwordId = null,
            submitId = null,
            webDomain = "www.euro.com.pl",
            packageName = "com.android.chrome",
        )
        val passwordStage = loginStage.copy(webDomain = "euro.com.pl")
        val unrelated = loginStage.copy(webDomain = "evil-euro.com.pl")

        assertTrue(AutofillSessionPolicy.sameTarget(loginStage, passwordStage))
        assertFalse(AutofillSessionPolicy.sameTarget(unrelated, passwordStage))
        assertEquals(
            2,
            AutofillSessionPolicy.merge(
                forms = listOf(loginStage, unrelated, passwordStage),
                current = passwordStage,
            ).contextCount,
        )
    }

    @Test
    fun `native session contexts require the exact same package`() {
        val first = AutofillForm(null, null, null, null, "com.example.shop")
        val second = AutofillForm(null, null, null, null, "com.example.shop")
        val attacker = AutofillForm(null, null, null, null, "com.example.fake")

        assertTrue(AutofillSessionPolicy.sameTarget(first, second))
        assertFalse(AutofillSessionPolicy.sameTarget(attacker, second))
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
