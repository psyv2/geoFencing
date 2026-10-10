package com.geogroup.shared.geo

import com.geogroup.shared.model.FenceState
import com.geogroup.shared.model.Geofence
import com.geogroup.shared.model.LocationSample

/** Tuning for [GeofenceEvaluator]. Defaults favour fewer false alarms over faster detection. */
data class GeofenceEvaluatorConfig(
    /** Dead band around the edge, so boundary jitter cannot flip the state. */
    val hysteresisMeters: Double = 25.0,

    val maxAcceptableAccuracyMeters: Double = 100.0,

    /** Requires the whole accuracy circle to clear the fence before reporting an exit. */
    val requireAccuracyConfidenceForExit: Boolean = true,
) {
    init {
        require(hysteresisMeters >= 0.0) { "hysteresisMeters must be >= 0" }
        require(maxAcceptableAccuracyMeters > 0.0) { "maxAcceptableAccuracyMeters must be > 0" }
    }
}

enum class FenceTransition {
    NONE,
    EXITED,
    RETURNED,
}

/** Why a sample did not change the fence state. */
enum class SampleRejection {
    NOT_REJECTED,
    INVALID_FIX,
    ACCURACY_TOO_LOW,
    WITHIN_DEAD_BAND,

    /** Past the edge, but the accuracy circle still overlaps the fence. */
    NOT_CONFIDENT_ENOUGH,
}

data class FenceEvaluation(
    val state: FenceState,
    val transition: FenceTransition,
    val distanceFromCenterMeters: Double,
    /** Negative inside, positive outside. */
    val signedDistanceFromEdgeMeters: Double,
    val rejection: SampleRejection,
) {
    val didAcceptSample: Boolean
        get() = rejection == SampleRejection.NOT_REJECTED || rejection == SampleRejection.WITHIN_DEAD_BAND
}

/**
 * Decides whether a position is inside or outside a fence. Pure function of
 * (previous state, fence, sample): no clocks, no storage, no coroutines.
 */
class GeofenceEvaluator(
    val config: GeofenceEvaluatorConfig = GeofenceEvaluatorConfig(),
) {

    fun evaluate(
        previousState: FenceState,
        fence: Geofence,
        sample: LocationSample,
    ): FenceEvaluation {
        val distance = GeoMath.distanceMeters(fence.center, sample.coordinate)
        val signedFromEdge = distance - fence.radiusMeters

        // CoreLocation reports a negative accuracy when the fix is unusable.
        if (sample.horizontalAccuracyMeters < 0.0) {
            return hold(previousState, distance, signedFromEdge, SampleRejection.INVALID_FIX)
        }
        if (sample.horizontalAccuracyMeters > config.maxAcceptableAccuracyMeters) {
            return hold(previousState, distance, signedFromEdge, SampleRejection.ACCURACY_TOO_LOW)
        }

        val exitThreshold = fence.radiusMeters + config.hysteresisMeters
        val enterThreshold = fence.radiusMeters - config.hysteresisMeters

        return when (previousState) {
            // First usable fix adopts a state but never reports a transition.
            FenceState.UNKNOWN -> FenceEvaluation(
                state = if (distance <= fence.radiusMeters) FenceState.INSIDE else FenceState.OUTSIDE,
                transition = FenceTransition.NONE,
                distanceFromCenterMeters = distance,
                signedDistanceFromEdgeMeters = signedFromEdge,
                rejection = SampleRejection.NOT_REJECTED,
            )

            FenceState.INSIDE -> {
                if (distance < exitThreshold) {
                    hold(FenceState.INSIDE, distance, signedFromEdge, SampleRejection.WITHIN_DEAD_BAND)
                } else if (config.requireAccuracyConfidenceForExit &&
                    distance - sample.horizontalAccuracyMeters <= fence.radiusMeters
                ) {
                    hold(FenceState.INSIDE, distance, signedFromEdge, SampleRejection.NOT_CONFIDENT_ENOUGH)
                } else {
                    FenceEvaluation(
                        state = FenceState.OUTSIDE,
                        transition = FenceTransition.EXITED,
                        distanceFromCenterMeters = distance,
                        signedDistanceFromEdgeMeters = signedFromEdge,
                        rejection = SampleRejection.NOT_REJECTED,
                    )
                }
            }

            FenceState.OUTSIDE -> {
                if (distance > enterThreshold) {
                    hold(FenceState.OUTSIDE, distance, signedFromEdge, SampleRejection.WITHIN_DEAD_BAND)
                } else {
                    FenceEvaluation(
                        state = FenceState.INSIDE,
                        transition = FenceTransition.RETURNED,
                        distanceFromCenterMeters = distance,
                        signedDistanceFromEdgeMeters = signedFromEdge,
                        rejection = SampleRejection.NOT_REJECTED,
                    )
                }
            }
        }
    }

    private fun hold(
        state: FenceState,
        distance: Double,
        signedFromEdge: Double,
        rejection: SampleRejection,
    ) = FenceEvaluation(
        state = state,
        transition = FenceTransition.NONE,
        distanceFromCenterMeters = distance,
        signedDistanceFromEdgeMeters = signedFromEdge,
        rejection = rejection,
    )
}
