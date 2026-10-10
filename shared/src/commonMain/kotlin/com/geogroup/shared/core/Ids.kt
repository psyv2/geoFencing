package com.geogroup.shared.core

import kotlin.random.Random

/** Opaque local identifiers. Internal: the platform layer never mints them. */
internal object Ids {

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun generate(random: Random, prefix: String, length: Int = 12): String =
        buildString(prefix.length + 1 + length) {
            append(prefix)
            append('_')
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
}
