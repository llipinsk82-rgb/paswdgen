package com.blackserv.passwdgen

import java.security.SecureRandom
import kotlin.math.ln

internal data class PasswordOptions(
    val length: Int = 16,
    val lowerCase: Boolean = true,
    val upperCase: Boolean = true,
    val digits: Boolean = true,
    val special: Boolean = true,
    val avoidAmbiguous: Boolean = true,
)

internal data class GeneratedPassword(
    val value: String,
    val entropyBits: Int,
)

internal object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 32

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#$%^&*()-_=+[]{};:,.?"
    private const val AMBIGUOUS = "Il1O0o|`'\""

    fun generate(
        options: PasswordOptions,
        random: SecureRandom = SecureRandom(),
    ): GeneratedPassword {
        require(options.length in MIN_LENGTH..MAX_LENGTH) {
            "Długość hasła musi mieścić się w zakresie $MIN_LENGTH–$MAX_LENGTH."
        }

        val groups = buildList {
            if (options.lowerCase) add(filter(LOWER, options.avoidAmbiguous))
            if (options.upperCase) add(filter(UPPER, options.avoidAmbiguous))
            if (options.digits) add(filter(DIGITS, options.avoidAmbiguous))
            if (options.special) add(filter(SPECIAL, options.avoidAmbiguous))
        }.filter { it.isNotEmpty() }

        require(groups.isNotEmpty()) { "Wybierz co najmniej jeden zestaw znaków." }
        require(options.length >= groups.size) { "Hasło jest za krótkie dla wybranych zestawów znaków." }

        val allCharacters = groups.joinToString(separator = "")
        val output = ArrayList<Char>(options.length)

        groups.forEach { group -> output += group[random.nextInt(group.length)] }
        repeat(options.length - output.size) {
            output += allCharacters[random.nextInt(allCharacters.length)]
        }

        for (index in output.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val temporary = output[index]
            output[index] = output[swapIndex]
            output[swapIndex] = temporary
        }

        val entropy = (options.length * (ln(allCharacters.length.toDouble()) / ln(2.0))).toInt()
        return GeneratedPassword(output.joinToString(separator = ""), entropy)
    }

    private fun filter(source: String, avoidAmbiguous: Boolean): String =
        if (avoidAmbiguous) source.filterNot(AMBIGUOUS::contains) else source
}
