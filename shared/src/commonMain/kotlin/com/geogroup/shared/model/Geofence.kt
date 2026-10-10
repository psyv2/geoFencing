package com.geogroup.shared.model

import kotlinx.serialization.Serializable

/** Circular region a group is tracked against. */
@Serializable
data class Geofence(
    val center: Coordinate,
    val radiusMeters: Double,
    val label: String,
) {
    init {
        require(radiusMeters >= MIN_RADIUS_METERS) {
            "radiusMeters must be >= $MIN_RADIUS_METERS, was $radiusMeters"
        }
        require(radiusMeters <= MAX_RADIUS_METERS) {
            "radiusMeters must be <= $MAX_RADIUS_METERS, was $radiusMeters"
        }
    }

    companion object {
        const val MIN_RADIUS_METERS: Double = 50.0
        const val MAX_RADIUS_METERS: Double = 50_000.0

        /** Clamps instead of throwing, so a slider can be wired straight in. */
        fun clamped(center: Coordinate, radiusMeters: Double, label: String): Geofence =
            Geofence(
                center = center,
                radiusMeters = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS),
                label = label,
            )
    }
}
