package com.geogroup.shared.geo

import com.geogroup.shared.model.Coordinate
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spherical-earth geometry. Within ~0.3% of an ellipsoidal solution, well under the
 * error of a consumer GPS fix.
 */
object GeoMath {

    /** IUGG mean earth radius. */
    const val EARTH_RADIUS_METERS: Double = 6_371_008.8

    private const val DEG_TO_RAD: Double = 0.017453292519943295
    private const val RAD_TO_DEG: Double = 57.29577951308232

    /** Great-circle distance in metres (haversine). */
    fun distanceMeters(from: Coordinate, to: Coordinate): Double {
        val lat1 = from.latitude * DEG_TO_RAD
        val lat2 = to.latitude * DEG_TO_RAD
        val halfDLat = (to.latitude - from.latitude) * DEG_TO_RAD / 2.0
        val halfDLon = (to.longitude - from.longitude) * DEG_TO_RAD / 2.0

        val a = sin(halfDLat) * sin(halfDLat) +
            cos(lat1) * cos(lat2) * sin(halfDLon) * sin(halfDLon)
        // min() guards against rounding pushing the value past 1 at antipodes.
        return 2.0 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /** Initial bearing from [from] to [to], in degrees clockwise from true north (0..360). */
    fun bearingDegrees(from: Coordinate, to: Coordinate): Double {
        val lat1 = from.latitude * DEG_TO_RAD
        val lat2 = to.latitude * DEG_TO_RAD
        val dLon = (to.longitude - from.longitude) * DEG_TO_RAD

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x) * RAD_TO_DEG + 360.0) % 360.0
    }

    /** The point reached by travelling [distanceMeters] from [origin] along [bearingDegrees]. */
    fun offset(origin: Coordinate, distanceMeters: Double, bearingDegrees: Double): Coordinate {
        val angular = distanceMeters / EARTH_RADIUS_METERS
        val bearing = bearingDegrees * DEG_TO_RAD
        val lat1 = origin.latitude * DEG_TO_RAD
        val lon1 = origin.longitude * DEG_TO_RAD

        val sinLat2 = sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing)
        val lat2 = asin(sinLat2.coerceIn(-1.0, 1.0))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sinLat2,
        )

        return Coordinate(
            latitude = lat2 * RAD_TO_DEG,
            // Normalise into -180..180 so paths across the antimeridian stay valid.
            longitude = ((lon2 * RAD_TO_DEG + 540.0) % 360.0) - 180.0,
        )
    }

    /** Moves [from] toward [to] by at most [distanceMeters], stopping exactly on arrival. */
    fun moveToward(from: Coordinate, to: Coordinate, distanceMeters: Double): Coordinate {
        val remaining = distanceMeters(from, to)
        if (remaining <= distanceMeters || remaining < 0.01) return to
        return offset(from, distanceMeters, bearingDegrees(from, to))
    }

    /** Metres per degree of longitude at a given latitude. */
    fun metersPerDegreeLongitude(atLatitude: Double): Double =
        EARTH_RADIUS_METERS * DEG_TO_RAD * cos(abs(atLatitude) * DEG_TO_RAD)
}
