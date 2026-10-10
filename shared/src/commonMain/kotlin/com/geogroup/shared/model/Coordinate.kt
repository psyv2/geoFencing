package com.geogroup.shared.model

import kotlinx.serialization.Serializable

/** WGS-84 position, in degrees. */
@Serializable
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)
