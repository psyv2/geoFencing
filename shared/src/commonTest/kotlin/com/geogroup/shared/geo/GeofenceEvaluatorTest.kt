package com.geogroup.shared.geo

import com.geogroup.shared.model.Coordinate
import com.geogroup.shared.model.FenceState
import com.geogroup.shared.model.Geofence
import com.geogroup.shared.model.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeofenceEvaluatorTest {

    private val center = Coordinate(28.6129, 77.2295)
    private val fence = Geofence(center = center, radiusMeters = 300.0, label = "Home")
    private val evaluator = GeofenceEvaluator()

    private fun sampleAt(
        distanceFromCenter: Double,
        accuracy: Double = 5.0,
        bearing: Double = 45.0,
    ) = LocationSample(
        coordinate = GeoMath.offset(center, distanceFromCenter, bearing),
        horizontalAccuracyMeters = accuracy,
        timestampEpochMillis = 1_700_000_000_000L,
    )

    // ------------------------------------------------------------ first fix

    @Test
    fun first_fix_inside_adopts_inside_without_a_transition() {
        val result = evaluator.evaluate(FenceState.UNKNOWN, fence, sampleAt(100.0))
        assertEquals(FenceState.INSIDE, result.state)
        assertEquals(FenceTransition.NONE, result.transition)
    }

    @Test
    fun first_fix_outside_does_not_report_an_exit() {
        // Opening the app while already away is not someone leaving.
        val result = evaluator.evaluate(FenceState.UNKNOWN, fence, sampleAt(5_000.0))
        assertEquals(FenceState.OUTSIDE, result.state)
        assertEquals(FenceTransition.NONE, result.transition)
    }

    // --------------------------------------------------------------- exiting

    @Test
    fun crossing_the_edge_but_staying_in_the_dead_band_holds_inside() {
        // 310 m out of a 300 m fence, with 25 m of hysteresis: past the line, not past
        // the band.
        val result = evaluator.evaluate(FenceState.INSIDE, fence, sampleAt(310.0))
        assertEquals(FenceState.INSIDE, result.state)
        assertEquals(FenceTransition.NONE, result.transition)
        assertEquals(SampleRejection.WITHIN_DEAD_BAND, result.rejection)
    }

    @Test
    fun clearing_the_dead_band_reports_an_exit() {
        val result = evaluator.evaluate(FenceState.INSIDE, fence, sampleAt(400.0))
        assertEquals(FenceState.OUTSIDE, result.state)
        assertEquals(FenceTransition.EXITED, result.transition)
        assertEquals(100.0, result.signedDistanceFromEdgeMeters, absoluteTolerance = 1.0)
    }

    @Test
    fun a_blurry_fix_cannot_trigger_an_exit() {
        // 400 m from centre sounds like an exit, but +/-150 m of accuracy means the
        // member might still be inside. Holding is the correct answer.
        val result = evaluator.evaluate(FenceState.INSIDE, fence, sampleAt(400.0, accuracy = 150.0))
        assertEquals(FenceState.INSIDE, result.state)
        assertEquals(FenceTransition.NONE, result.transition)
        assertEquals(SampleRejection.ACCURACY_TOO_LOW, result.rejection)
    }

    @Test
    fun an_exit_requires_the_whole_accuracy_circle_to_be_outside() {
        // 340 m out, +/-60 m: the circle still overlaps the 300 m fence.
        val overlapping = evaluator.evaluate(FenceState.INSIDE, fence, sampleAt(340.0, accuracy = 60.0))
        assertEquals(FenceTransition.NONE, overlapping.transition)
        assertEquals(SampleRejection.NOT_CONFIDENT_ENOUGH, overlapping.rejection)

        // Same accuracy, further out: now unambiguous.
        val clear = evaluator.evaluate(FenceState.INSIDE, fence, sampleAt(400.0, accuracy = 60.0))
        assertEquals(FenceTransition.EXITED, clear.transition)
    }

    @Test
    fun a_negative_accuracy_fix_is_discarded() {
        val result = evaluator.evaluate(FenceState.INSIDE, fence, sampleAt(5_000.0, accuracy = -1.0))
        assertEquals(FenceState.INSIDE, result.state)
        assertEquals(SampleRejection.INVALID_FIX, result.rejection)
        assertTrue(!result.didAcceptSample)
    }

    // -------------------------------------------------------------- returning

    @Test
    fun returning_requires_crossing_back_past_the_dead_band() {
        val stillInBand = evaluator.evaluate(FenceState.OUTSIDE, fence, sampleAt(290.0))
        assertEquals(FenceState.OUTSIDE, stillInBand.state)
        assertEquals(FenceTransition.NONE, stillInBand.transition)

        val wellInside = evaluator.evaluate(FenceState.OUTSIDE, fence, sampleAt(250.0))
        assertEquals(FenceState.INSIDE, wellInside.state)
        assertEquals(FenceTransition.RETURNED, wellInside.transition)
    }

    @Test
    fun returning_does_not_need_accuracy_confidence() {
        // The confidence rule exists to suppress false *alarms*. Being slow to confirm a
        // safe return would be the more annoying failure, so it is not applied here.
        val result = evaluator.evaluate(FenceState.OUTSIDE, fence, sampleAt(200.0, accuracy = 90.0))
        assertEquals(FenceTransition.RETURNED, result.transition)
    }

    // ------------------------------------------------------------- stability

    @Test
    fun standing_on_the_boundary_never_flaps() {
        // Someone loitering on the fence line, jittering either side of it. With
        // hysteresis this must produce exactly zero transitions.
        var state = FenceState.INSIDE
        var transitions = 0
        val jitter = listOf(295.0, 305.0, 298.0, 312.0, 290.0, 308.0, 301.0, 296.0, 311.0)

        for (distance in jitter) {
            val result = evaluator.evaluate(state, fence, sampleAt(distance))
            if (result.transition != FenceTransition.NONE) transitions++
            state = result.state
        }

        assertEquals(0, transitions, "hysteresis failed to suppress boundary jitter")
        assertEquals(FenceState.INSIDE, state)
    }

    @Test
    fun a_full_round_trip_reports_one_exit_and_one_return() {
        var state = FenceState.UNKNOWN
        val transitions = mutableListOf<FenceTransition>()

        for (distance in listOf(50.0, 150.0, 310.0, 450.0, 900.0, 400.0, 310.0, 200.0, 80.0)) {
            val result = evaluator.evaluate(state, fence, sampleAt(distance))
            if (result.transition != FenceTransition.NONE) transitions += result.transition
            state = result.state
        }

        assertEquals(listOf(FenceTransition.EXITED, FenceTransition.RETURNED), transitions)
        assertEquals(FenceState.INSIDE, state)
    }

    @Test
    fun zero_hysteresis_flips_exactly_at_the_edge() {
        val strict = GeofenceEvaluator(
            GeofenceEvaluatorConfig(hysteresisMeters = 0.0, requireAccuracyConfidenceForExit = false),
        )
        assertEquals(
            FenceTransition.EXITED,
            strict.evaluate(FenceState.INSIDE, fence, sampleAt(301.0)).transition,
        )
        assertEquals(
            FenceTransition.NONE,
            strict.evaluate(FenceState.INSIDE, fence, sampleAt(299.0)).transition,
        )
    }

    @Test
    fun signed_distance_is_negative_inside_and_positive_outside() {
        val inside = evaluator.evaluate(FenceState.INSIDE, fence, sampleAt(100.0))
        assertEquals(-200.0, inside.signedDistanceFromEdgeMeters, absoluteTolerance = 1.0)

        val outside = evaluator.evaluate(FenceState.OUTSIDE, fence, sampleAt(500.0))
        assertEquals(200.0, outside.signedDistanceFromEdgeMeters, absoluteTolerance = 1.0)
    }
}
