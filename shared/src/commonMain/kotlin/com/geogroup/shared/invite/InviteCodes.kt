package com.geogroup.shared.invite

import kotlin.random.Random

/**
 * Six-character invite codes in Crockford base32, which drops I, L, O and U so a code
 * read aloud cannot be garbled into a different valid one. [normalize] folds the
 * characters people do mistype back to canonical form.
 */
object InviteCodes {

    const val LENGTH: Int = 6

    const val ALPHABET: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun generate(random: Random): String =
        buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    /** Uppercases, strips separators, folds look-alikes. */
    fun normalize(raw: String): String =
        buildString(raw.length) {
            for (char in raw.uppercase()) {
                when (char) {
                    ' ', '-', '_', '\t', '\n' -> Unit
                    'I', 'L' -> append('1')
                    'O' -> append('0')
                    'U' -> append('V')
                    else -> append(char)
                }
            }
        }

    fun isValid(raw: String): Boolean {
        val normalized = normalize(raw)
        return normalized.length == LENGTH && normalized.all { it in ALPHABET }
    }

    /** Groups the code for display: `TREK42` -> `TRE-K42`. */
    fun formatForDisplay(code: String): String {
        val normalized = normalize(code)
        if (normalized.length != LENGTH) return normalized
        return normalized.substring(0, 3) + "-" + normalized.substring(3)
    }
}
