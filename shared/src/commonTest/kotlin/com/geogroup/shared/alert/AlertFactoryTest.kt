package com.geogroup.shared.alert

import kotlin.test.Test
import kotlin.test.assertEquals

class AlertFactoryTest {

    @Test
    fun distances_under_a_kilometre_are_whole_metres() {
        assertEquals("0 m", AlertFactory.formatDistance(0.0))
        assertEquals("1 m", AlertFactory.formatDistance(1.4))
        assertEquals("2 m", AlertFactory.formatDistance(1.5))
        assertEquals("250 m", AlertFactory.formatDistance(250.0))
        assertEquals("999 m", AlertFactory.formatDistance(999.4))
    }

    @Test
    fun a_kilometre_and_beyond_gets_one_decimal() {
        assertEquals("1.0 km", AlertFactory.formatDistance(1_000.0))
        assertEquals("1.5 km", AlertFactory.formatDistance(1_460.0))
        assertEquals("12.3 km", AlertFactory.formatDistance(12_345.0))
    }

    @Test
    fun a_negative_distance_reads_as_zero_rather_than_minus() {
        // Can happen if a fence is resized mid-evaluation. "-4 m outside" is nonsense,
        // so it is floored instead of printed.
        assertEquals("0 m", AlertFactory.formatDistance(-4.0))
    }
}
