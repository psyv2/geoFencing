package com.geogroup.shared.model

import kotlinx.serialization.Serializable

/** A single positional fix: the `CLLocation` fields that affect a fence decision. */
@Serializable
data class LocationSample(
    val coordinate: Coordinate,
    /** Negative means an invalid fix, following CoreLocation's convention. */
    val horizontalAccuracyMeters: Double,
    val timestampEpochMillis: Long,
)
