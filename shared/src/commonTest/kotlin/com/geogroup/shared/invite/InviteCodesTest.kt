package com.geogroup.shared.invite

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteCodesTest {

    @Test
    fun generated_codes_are_six_characters_from_the_alphabet() {
        val random = Random(7)
        repeat(500) {
            val code = InviteCodes.generate(random)
            assertEquals(InviteCodes.LENGTH, code.length)
            assertTrue(code.all { it in InviteCodes.ALPHABET }, "unexpected character in $code")
            assertTrue(InviteCodes.isValid(code))
        }
    }

    @Test
    fun the_alphabet_excludes_every_confusable_character() {
        // Crockford's omissions: I and L look like 1, O looks like 0, U can be misread
        // as V (and makes accidental words).
        for (char in listOf('I', 'L', 'O', 'U')) {
            assertFalse(char in InviteCodes.ALPHABET, "$char should not be generatable")
        }
        assertEquals(32, InviteCodes.ALPHABET.length)
    }

    @Test
    fun normalize_uppercases_and_strips_separators() {
        assertEquals("TREK42", InviteCodes.normalize("trek42"))
        assertEquals("TREK42", InviteCodes.normalize("TRE-K42"))
        assertEquals("TREK42", InviteCodes.normalize(" trek 42 "))
        assertEquals("TREK42", InviteCodes.normalize("TRE_K42"))
    }

    @Test
    fun normalize_folds_lookalikes_onto_the_canonical_character() {
        assertEquals("1", InviteCodes.normalize("I"))
        assertEquals("1", InviteCodes.normalize("l"))
        assertEquals("0", InviteCodes.normalize("o"))
        assertEquals("V", InviteCodes.normalize("u"))
        // A code mistyped with every lookalike still resolves to the real one.
        assertEquals("H0N1V5", InviteCodes.normalize("hOnIu5"))
    }

    @Test
    fun validity_rejects_wrong_length_and_stray_characters() {
        assertFalse(InviteCodes.isValid(""))
        assertFalse(InviteCodes.isValid("ABC12"))
        assertFalse(InviteCodes.isValid("ABC1234"))
        assertFalse(InviteCodes.isValid("ABC!23"))
        assertTrue(InviteCodes.isValid("ABC123"))
    }

    @Test
    fun normalization_is_idempotent() {
        val random = Random(99)
        repeat(200) {
            val code = InviteCodes.generate(random)
            assertEquals(code, InviteCodes.normalize(code))
            assertEquals(code, InviteCodes.normalize(InviteCodes.normalize(code)))
        }
    }

    @Test
    fun display_formatting_groups_the_code_and_survives_round_trip() {
        assertEquals("TRE-K42", InviteCodes.formatForDisplay("trek42"))
        assertEquals("TREK42", InviteCodes.normalize(InviteCodes.formatForDisplay("TREK42")))
    }

    @Test
    fun display_formatting_leaves_malformed_input_alone() {
        assertEquals("ABC", InviteCodes.formatForDisplay("abc"))
    }

    @Test
    fun codes_do_not_collide_often_enough_to_matter() {
        val random = Random(5)
        val codes = (1..5_000).map { InviteCodes.generate(random) }.toSet()
        // 32^6 is ~1.07e9, so 5000 draws should essentially never repeat.
        assertTrue(codes.size >= 4_995, "too many collisions: ${codes.size} unique of 5000")
    }
}
