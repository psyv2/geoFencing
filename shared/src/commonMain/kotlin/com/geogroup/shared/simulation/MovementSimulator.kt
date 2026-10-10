package com.geogroup.shared.simulation

import com.geogroup.shared.geo.GeoMath
import com.geogroup.shared.model.BreachAlert
import com.geogroup.shared.model.Coordinate
import com.geogroup.shared.model.FenceState
import com.geogroup.shared.model.Geofence
import com.geogroup.shared.model.LocationSample
import com.geogroup.shared.platform.TimeProvider
import com.geogroup.shared.store.GroupStore
import kotlin.random.Random

/**
 * Moves members so fence crossings can be observed without walking outdoors with five
 * phones. Fabricates positions only: every sample goes through
 * [GroupStore.recordLocation], the same entry point real fixes use.
 */
class MovementSimulator internal constructor(
    private val store: GroupStore,
    private val time: TimeProvider,
    private val random: Random,
) {

    /** Where a simulated member is heading, and how fast. */
    private data class Walk(
        val target: Coordinate,
        val speedMetersPerSecond: Double,
    )

    private val walks: MutableMap<String, Walk> = mutableMapOf()

    /**
     * Advances every non-self member by [elapsedSeconds] of travel. Self is excluded:
     * that position comes from the device.
     */
    fun tick(elapsedSeconds: Double): List<BreachAlert> {
        val alerts = mutableListOf<BreachAlert>()

        for (group in store.current.groups) {
            for (member in group.members) {
                if (member.isSelf) continue
                val from = member.lastKnownLocation ?: group.geofence.center
                val key = walkKey(group.id, member.id)
                val walk = walks.getOrPut(key) { newWalk(group.geofence) }

                val next = GeoMath.moveToward(
                    from = from,
                    to = walk.target,
                    distanceMeters = walk.speedMetersPerSecond * elapsedSeconds,
                )

                // Arrived, so pick a new target for the next tick.
                if (GeoMath.distanceMeters(next, walk.target) < ARRIVAL_TOLERANCE_METERS) {
                    walks[key] = newWalk(group.geofence)
                }

                alerts += store.recordLocation(member.id, sampleAt(next))
            }
        }
        return alerts
    }

    /**
     * Teleports a member clear of the fence. A member still in [FenceState.UNKNOWN] is
     * placed at the centre first, since a transition needs a known INSIDE to leave from.
     */
    fun placeOutside(groupId: String, memberId: String): List<BreachAlert> {
        val group = store.current.group(groupId) ?: return emptyList()
        if (group.member(memberId) == null) return emptyList()

        val alerts = mutableListOf<BreachAlert>()
        if (group.member(memberId)?.fenceState != FenceState.INSIDE) {
            alerts += store.recordLocation(memberId, sampleAt(group.geofence.center))
        }

        val outside = GeoMath.offset(
            origin = group.geofence.center,
            distanceMeters = group.geofence.radiusMeters + OUTSIDE_OVERSHOOT_METERS,
            bearingDegrees = random.nextDouble() * 360.0,
        )
        walks[walkKey(groupId, memberId)] = Walk(outside, speedFor(group.geofence))
        alerts += store.recordLocation(memberId, sampleAt(outside))
        return alerts
    }

    /** Teleports a member back to the middle of the fence. */
    fun placeInside(groupId: String, memberId: String): List<BreachAlert> {
        val group = store.current.group(groupId) ?: return emptyList()
        if (group.member(memberId) == null) return emptyList()

        val inside = GeoMath.offset(
            origin = group.geofence.center,
            distanceMeters = group.geofence.radiusMeters * 0.25,
            bearingDegrees = random.nextDouble() * 360.0,
        )
        walks[walkKey(groupId, memberId)] = Walk(inside, speedFor(group.geofence))
        return store.recordLocation(memberId, sampleAt(inside))
    }

    fun forgetGroup(groupId: String) {
        walks.keys.filter { it.startsWith("$groupId|") }.forEach { walks.remove(it) }
    }

    // ----------------------------------------------------------------- helpers

    private fun walkKey(groupId: String, memberId: String) = "$groupId|$memberId"

    private fun newWalk(geofence: Geofence): Walk {
        // Weighted so crossings happen often enough to watch.
        val goesOutside = random.nextDouble() < OUTSIDE_TARGET_PROBABILITY
        val distance = if (goesOutside) {
            geofence.radiusMeters * (1.15 + random.nextDouble() * 0.45)
        } else {
            geofence.radiusMeters * random.nextDouble() * 0.8
        }
        return Walk(
            target = GeoMath.offset(
                origin = geofence.center,
                distanceMeters = distance,
                bearingDegrees = random.nextDouble() * 360.0,
            ),
            speedMetersPerSecond = speedFor(geofence),
        )
    }

    /**
     * Scaled to the fence so a crossing takes about ten seconds either way. Deliberately
     * faster than walking pace, which would make a 2 km zone untestable by hand.
     */
    private fun speedFor(geofence: Geofence): Double =
        (geofence.radiusMeters / 12.0).coerceIn(2.0, 400.0)

    private fun sampleAt(coordinate: Coordinate) = LocationSample(
        coordinate = coordinate,
        // Tight accuracy: the evaluator's gating has its own unit tests.
        horizontalAccuracyMeters = 5.0,
        timestampEpochMillis = time.nowEpochMillis(),
    )

    private companion object {
        const val ARRIVAL_TOLERANCE_METERS = 5.0
        const val OUTSIDE_OVERSHOOT_METERS = 80.0
        const val OUTSIDE_TARGET_PROBABILITY = 0.35
    }
}
