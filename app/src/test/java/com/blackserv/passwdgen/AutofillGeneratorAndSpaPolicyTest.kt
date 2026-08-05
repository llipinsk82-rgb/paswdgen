package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillGeneratorAndSpaPolicyTest {
    @Test
    fun `generated Autofill password uses strong private defaults`() {
        repeat(32) {
            val password = AutofillGeneratedPasswordPolicy.generate()

            assertEquals(AutofillGeneratedPasswordPolicy.LENGTH, password.length)
            assertTrue(password.any(Char::isLowerCase))
            assertTrue(password.any(Char::isUpperCase))
            assertTrue(password.any(Char::isDigit))
            assertTrue(password.any { character -> character in "!@#$%^&*()-_=+[]{};:,.?" })
            assertFalse(password.any { character -> character in "Il1O0o|`'\"" })
        }
    }

    @Test
    fun `clickable SPA registration action is accepted`() {
        assertTrue(
            AutofillSpaSubmitPolicy.isActionCandidate(
                htmlTag = "div",
                htmlAttributes = listOf("data-testid" to "registration-submit"),
                className = "android.view.View",
                text = "Załóż konto",
                contentDescription = null,
                idEntry = null,
                clickable = true,
            ),
        )
    }

    @Test
    fun `passive SPA action text is rejected`() {
        assertFalse(
            AutofillSpaSubmitPolicy.isActionCandidate(
                htmlTag = "div",
                htmlAttributes = emptyList(),
                className = "android.view.View",
                text = "Załóż konto",
                contentDescription = null,
                idEntry = null,
                clickable = false,
            ),
        )
    }

    @Test
    fun `unrelated clickable control is rejected`() {
        assertFalse(
            AutofillSpaSubmitPolicy.isActionCandidate(
                htmlTag = "div",
                htmlAttributes = listOf("role" to "button"),
                className = "android.view.View",
                text = "Pokaż regulamin",
                contentDescription = null,
                idEntry = "terms",
                clickable = true,
            ),
        )
    }
}
