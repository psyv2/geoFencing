package com.geogroup.shared.geo

import com.geogroup.shared.model.Coordinate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoMathTest {

    @Test
    fun distance_between_identical_points_is_zero() {
        val point = Coordinate(28.6129, 77.2295)
        assertEquals(0.0, GeoMath.distanceMeters(point, point), absoluteTolerance = 1e-9)
    }

    @Test
    fun one_degree_of_latitude_is_about_111_km() {
        val distance = GeoMath.distanceMeters(
            Coordinate(0.0, 0.0),
            Coordinate(1.0, 0.0),
        )
        // Mean-earth meridian degree is 111.195 km; allow 100 m of model slack.
        assertTrue(
            abs(distance - 111_195.0) < 100.0,
            "expected ~111195 m, got $distance",
        )
    }

    @Test
    fun longitude_degrees_shrink_toward_the_poles() {
        val atEquator = GeoMath.distanceMeters(Coordinate(0.0, 0.0), Coordinate(0.0, 1.0))
        val atSixty = GeoMath.distanceMeters(Coordinate(60.0, 0.0), Coordinate(60.0, 1.0))
        // cos(60 deg) = 0.5, so the spacing should roughly halve.
        assertTrue(
            abs(atSixty / atEquator - 0.5) < 0.01,
            "expected ratio ~0.5, got ${atSixty / atEquator}",
        )
    }

    @Test
    fun offset_then_measure_round_trips() {
        val origin = Coordinate(19.0760, 72.8777)
        for (bearing in listOf(0.0, 45.0, 90.0, 180.0, 275.0, 359.0)) {
            val moved = GeoMath.offset(origin, distanceMeters = 1_500.0, bearingDegrees = bearing)
            val measured = GeoMath.distanceMeters(origin, moved)
            assertTrue(
                abs(measured - 1_500.0) < 1.0,
                "bearing $bearing: expected 1500 m, got $measured",
            )
        }
    }

    @Test
    fun bearing_is_reported_clockwise_from_north() {
        val origin = Coordinate(0.0, 0.0)
        assertEquals(0.0, GeoMath.bearingDegrees(origin, Coordinate(1.0, 0.0)), absoluteTolerance = 0.01)
        assertEquals(90.0, GeoMath.bearingDegrees(origin, Coordinate(0.0, 1.0)), absoluteTolerance = 0.01)
        assertEquals(180.0, GeoMath.bearingDegrees(origin, Coordinate(-1.0, 0.0)), absoluteTolerance = 0.01)
        assertEquals(270.0, GeoMath.bearingDegrees(origin, Coordinate(0.0, -1.0)), absoluteTolerance = 0.01)
    }

    @Test
    fun offset_round_trips_through_bearing() {
        val origin = Coordinate(48.8566, 2.3522)
        val target = GeoMath.offset(origin, 3_000.0, 137.0)
        assertEquals(137.0, GeoMath.bearingDegrees(origin, target), absoluteTolerance = 0.5)
    }

    @Test
    fun move_toward_stops_exactly_on_arrival_and_never_overshoots() {
        val from = Coordinate(12.9716, 77.5946)
        val to = GeoMath.offset(from, 100.0, 90.0)

        val partial = GeoMath.moveToward(from, to, distanceMeters = 40.0)
        assertEquals(40.0, GeoMath.distanceMeters(from, partial), absoluteTolerance = 0.5)

        // Asking for more than the remaining distance must land on the target, not past it.
        val arrived = GeoMath.moveToward(from, to, distanceMeters = 500.0)
        assertEquals(to, arrived)
    }

    @Test
    fun offset_across_the_antimeridian_stays_in_range() {
        val nearDateLine = Coordinate(0.0, 179.99)
        val crossed = GeoMath.offset(nearDateLine, distanceMeters = 5_000.0, bearingDegrees = 90.0)
        assertTrue(
            crossed.longitude in -180.0..180.0,
            "longitude escaped valid range: ${crossed.longitude}",
        )
        // Distance must still be measurable across the seam.
        assertEquals(
            5_000.0,
            GeoMath.distanceMeters(nearDateLine, crossed),
            absoluteTolerance = 1.0,
        )
    }
}
