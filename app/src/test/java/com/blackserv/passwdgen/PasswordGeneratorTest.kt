package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test
    fun generatedPasswordHasRequestedLengthAndEverySelectedGroup() {
        val result = PasswordGenerator.generate(
            PasswordOptions(
                length = 32,
                lowerCase = true,
                upperCase = true,
                digits = true,
                special = true,
                avoidAmbiguous = false,
            ),
        )

        assertEquals(32, result.value.length)
        assertTrue(result.value.any(Char::isLowerCase))
        assertTrue(result.value.any(Char::isUpperCase))
        assertTrue(result.value.any(Char::isDigit))
        assertTrue(result.value.any { !it.isLetterOrDigit() })
        assertTrue(result.entropyBits > 180)
    }

    @Test
    fun ambiguousCharactersCanBeExcluded() {
        repeat(100) {
            val result = PasswordGenerator.generate(
                PasswordOptions(length = PasswordGenerator.MAX_LENGTH, avoidAmbiguous = true),
            )
            assertFalse(result.value.any { it in "Il1O0o|`'\"" })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun passwordsLongerThanMaximumAreRejected() {
        PasswordGenerator.generate(
            PasswordOptions(length = PasswordGenerator.MAX_LENGTH + 1),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun atLeastOneCharacterGroupIsRequired() {
        PasswordGenerator.generate(
            PasswordOptions(
                lowerCase = false,
                upperCase = false,
                digits = false,
                special = false,
            ),
        )
    }
}
