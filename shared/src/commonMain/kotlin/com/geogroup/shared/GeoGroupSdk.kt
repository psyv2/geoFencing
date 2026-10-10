package com.geogroup.shared

import com.geogroup.shared.alert.AlertPresenter
import com.geogroup.shared.geo.GeofenceEvaluator
import com.geogroup.shared.geo.GeofenceEvaluatorConfig
import com.geogroup.shared.model.BreachAlert
import com.geogroup.shared.model.Coordinate
import com.geogroup.shared.model.Geofence
import com.geogroup.shared.model.LocationSample
import com.geogroup.shared.model.SelfProfile
import com.geogroup.shared.model.TrackingGroup
import com.geogroup.shared.platform.TimeProvider
import com.geogroup.shared.platform.defaultKeyValueStore
import com.geogroup.shared.platform.systemTimeProvider
import com.geogroup.shared.simulation.MovementSimulator
import com.geogroup.shared.store.AppSnapshot
import com.geogroup.shared.store.GroupStore
import com.geogroup.shared.store.JoinOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The single entry point the iOS app talks to. Every decision lives behind this facade;
 * the platform layer only supplies location fixes and delivers notifications.
 *
 * Calls are synchronous: there is no server, so there is nothing to await.
 */
class GeoGroupSdk internal constructor(
    private val store: GroupStore,
    private val simulator: MovementSimulator,
    private val alertPresenter: AlertPresenter,
    private val time: TimeProvider,
    private val scope: CoroutineScope,
) {

    private var simulationJob: Job? = null

    // ------------------------------------------------------------- observation

    /** Current state. A single immutable value, already in memory. */
    val snapshot: AppSnapshot
        get() = store.current

    val profile: SelfProfile?
        get() = store.current.profile

    val isOnboarded: Boolean
        get() = store.current.isOnboarded

    /** Calls [onChange] with the current state, then on every mutation. */
    fun observeSnapshot(onChange: (AppSnapshot) -> Unit): Subscription =
        Subscription(
            scope.launch {
                store.snapshot.collect { onChange(it) }
            },
        )

    // ---------------------------------------------------------------- identity

    fun completeOnboarding(displayName: String): SelfProfile =
        store.completeOnboarding(displayName)

    // ------------------------------------------------------------------ groups

    /** Creates a group and its fence together. The radius is clamped, not validated. */
    fun createGroup(
        name: String,
        fenceLabel: String,
        centerLatitude: Double,
        centerLongitude: Double,
        radiusMeters: Double,
        addDemoMembers: Boolean,
    ): TrackingGroup = store.createGroup(
        name = name,
        geofence = Geofence.clamped(
            center = Coordinate(centerLatitude, centerLongitude),
            radiusMeters = radiusMeters,
            label = fenceLabel.trim().ifEmpty { "the zone" },
        ),
        addDemoMembers = addDemoMembers,
    )

    fun joinGroup(inviteCode: String): JoinOutcome = store.joinGroup(inviteCode)

    fun leaveGroup(groupId: String) {
        simulator.forgetGroup(groupId)
        store.leaveGroup(groupId)
    }

    fun deleteGroup(groupId: String) {
        simulator.forgetGroup(groupId)
        store.deleteGroup(groupId)
    }

    fun updateGeofence(
        groupId: String,
        fenceLabel: String,
        centerLatitude: Double,
        centerLongitude: Double,
        radiusMeters: Double,
    ) {
        store.updateGeofence(
            groupId = groupId,
            geofence = Geofence.clamped(
                center = Coordinate(centerLatitude, centerLongitude),
                radiusMeters = radiusMeters,
                label = fenceLabel.trim().ifEmpty { "the zone" },
            ),
        )
    }

    /** Creates a group this device is not in, so the join flow works on one phone. */
    fun createJoinableDemoGroup(nearLatitude: Double, nearLongitude: Double): TrackingGroup =
        store.createJoinableDemoGroup(Coordinate(nearLatitude, nearLongitude))

    // ---------------------------------------------------------------- location

    /**
     * Hands one fix to the evaluator and raises whatever alerts it decides on. Takes
     * primitives so the Swift delegate need not construct Kotlin values.
     */
    fun submitSelfLocation(
        latitude: Double,
        longitude: Double,
        horizontalAccuracyMeters: Double,
        timestampEpochMillis: Long,
    ) {
        val selfId = store.current.profile?.memberId ?: return
        dispatch(
            store.recordLocation(
                memberId = selfId,
                sample = LocationSample(
                    coordinate = Coordinate(latitude, longitude),
                    horizontalAccuracyMeters = horizontalAccuracyMeters,
                    timestampEpochMillis = timestampEpochMillis,
                ),
            ),
        )
    }

    // -------------------------------------------------------------- simulation

    val isSimulationRunning: Boolean
        get() = simulationJob?.isActive == true

    /** Moves simulated members every [TICK_SECONDS] seconds. */
    fun startSimulation() {
        if (isSimulationRunning) return
        simulationJob = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                dispatch(simulator.tick(TICK_SECONDS))
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    /** Pushes any member, including yourself, clear of the fence. */
    fun simulateExit(groupId: String, memberId: String) {
        dispatch(simulator.placeOutside(groupId, memberId))
    }

    fun simulateReturn(groupId: String, memberId: String) {
        dispatch(simulator.placeInside(groupId, memberId))
    }

    // ------------------------------------------------------------------- debug

    fun resetAllData() {
        stopSimulation()
        store.reset()
    }

    /** Releases the coroutine scope. The instance is unusable afterwards. */
    fun close() {
        stopSimulation()
        scope.cancel()
    }

    private fun dispatch(alerts: List<BreachAlert>) {
        for (alert in alerts) alertPresenter.present(alert)
    }

    companion object {
        const val TICK_MILLIS: Long = 2_000L
        const val TICK_SECONDS: Double = 2.0

        /** Wires up the platform storage and clock. */
        fun create(alertPresenter: AlertPresenter): GeoGroupSdk {
            val time = systemTimeProvider()
            val store = GroupStore(
                keyValueStore = defaultKeyValueStore(),
                time = time,
                evaluator = GeofenceEvaluator(GeofenceEvaluatorConfig()),
                random = Random.Default,
            )
            return GeoGroupSdk(
                store = store,
                simulator = MovementSimulator(store = store, time = time, random = Random.Default),
                alertPresenter = alertPresenter,
                time = time,
                // Main dispatcher, so callbacks land on the main queue for SwiftUI.
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            )
        }
    }
}
