package com.blackserv.passwdgen

internal object AutofillGeneratedPasswordPolicy {
    const val LENGTH = 20

    fun generate(): String = PasswordGenerator.generate(
        PasswordOptions(
            length = LENGTH,
            lowerCase = true,
            upperCase = true,
            digits = true,
            special = true,
            avoidAmbiguous = true,
        ),
    ).value
}
