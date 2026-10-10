package com.geogroup.shared.simulation

import com.geogroup.shared.DELHI
import com.geogroup.shared.TEST_EPOCH
import com.geogroup.shared.geo.GeoMath
import com.geogroup.shared.model.ActivityKind
import com.geogroup.shared.model.AlertSeverity
import com.geogroup.shared.model.FenceState
import com.geogroup.shared.model.Geofence
import com.geogroup.shared.platform.FixedTimeProvider
import com.geogroup.shared.store.GroupStore
import com.geogroup.shared.testStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MovementSimulatorTest {

    private val fence = Geofence(center = DELHI, radiusMeters = 300.0, label = "Home")

    private fun fixture(seed: Int = 99): Triple<GroupStore, MovementSimulator, FixedTimeProvider> {
        val time = FixedTimeProvider(TEST_EPOCH)
        val store = testStore(time = time, random = Random(seed))
        val simulator = MovementSimulator(store = store, time = time, random = Random(seed))
        return Triple(store, simulator, time)
    }

    @Test
    fun pushing_a_member_outside_notifies_the_group() {
        val (store, simulator, _) = fixture()
        store.completeOnboarding("Harshit")
        val group = store.createGroup("Family", fence, addDemoMembers = true)
        val peer = group.members.first { !it.isSelf }

        val alerts = simulator.placeOutside(group.id, peer.id)

        assertEquals(1, alerts.size)
        assertEquals(AlertSeverity.BREACH, alerts.single().severity)
        assertEquals(FenceState.OUTSIDE, store.current.group(group.id)?.member(peer.id)?.fenceState)
    }

    @Test
    fun pushing_a_member_with_no_fix_outside_still_produces_an_exit() {
        // Self starts UNKNOWN. A naive implementation would swallow this as a first fix
        // and the demo button would silently do nothing.
        val (store, simulator, _) = fixture()
        store.completeOnboarding("Harshit")
        val group = store.createGroup("Family", fence, addDemoMembers = true)
        val self = assertNotNull(group.selfMember)
        assertEquals(FenceState.UNKNOWN, self.fenceState)

        val alerts = simulator.placeOutside(group.id, self.id)

        assertEquals(AlertSeverity.SELF_CONFIRMATION, alerts.last().severity)
        assertEquals(FenceState.OUTSIDE, store.current.group(group.id)?.member(self.id)?.fenceState)
        assertEquals(ActivityKind.EXITED_FENCE, store.current.activity.first().kind)
    }

    @Test
    fun pulling_a_member_back_inside_reports_a_return() {
        val (store, simulator, _) = fixture()
        store.completeOnboarding("Harshit")
        val group = store.createGroup("Family", fence, addDemoMembers = true)
        val peer = group.members.first { !it.isSelf }
        simulator.placeOutside(group.id, peer.id)

        val alerts = simulator.placeInside(group.id, peer.id)

        assertEquals(AlertSeverity.INFO, alerts.single().severity)
        assertEquals(FenceState.INSIDE, store.current.group(group.id)?.member(peer.id)?.fenceState)
    }

    @Test
    fun ticking_moves_peers_but_never_touches_my_own_position() {
        val (store, simulator, _) = fixture()
        store.completeOnboarding("Harshit")
        val group = store.createGroup("Family", fence, addDemoMembers = true)
        val selfId = assertNotNull(group.selfMember).id

        // Give self a real position, as CoreLocation would.
        store.recordLocation(
            selfId,
            com.geogroup.shared.sampleAt(fence.center, distanceFromCenter = 42.0),
        )
        val selfBefore = store.current.group(group.id)?.member(selfId)?.lastKnownLocation
        val peerBefore = group.members.filterNot { it.isSelf }.associate { it.id to it.lastKnownLocation }

        repeat(10) { simulator.tick(elapsedSeconds = 2.0) }

        val after = assertNotNull(store.current.group(group.id))
        assertEquals(selfBefore, after.member(selfId)?.lastKnownLocation, "simulator must not move self")
        for ((peerId, before) in peerBefore) {
            assertTrue(
                after.member(peerId)?.lastKnownLocation != before,
                "peer $peerId never moved",
            )
        }
    }

    @Test
    fun a_long_run_eventually_produces_both_exits_and_returns() {
        val (store, simulator, time) = fixture(seed = 3)
        store.completeOnboarding("Harshit")
        val group = store.createGroup("Family", fence, addDemoMembers = true)

        val severities = mutableListOf<AlertSeverity>()
        repeat(400) {
            time.advance(2_000)
            severities += simulator.tick(elapsedSeconds = 2.0).map { it.severity }
        }

        assertTrue(AlertSeverity.BREACH in severities, "simulation never produced an exit")
        assertTrue(AlertSeverity.INFO in severities, "simulation never produced a return")
        assertTrue(
            store.current.activity.any { it.kind == ActivityKind.EXITED_FENCE },
            "no exit reached the activity feed",
        )
        assertEquals(group.id, store.current.activity.first().groupId)
    }

    @Test
    fun simulated_members_stay_in_the_neighbourhood_of_the_fence() {
        val (store, simulator, time) = fixture(seed = 11)
        store.completeOnboarding("Harshit")
        val group = store.createGroup("Family", fence, addDemoMembers = true)

        repeat(300) {
            time.advance(2_000)
            simulator.tick(elapsedSeconds = 2.0)
        }

        // Peers wander, but targets are always chosen relative to the fence radius.
        for (peer in assertNotNull(store.current.group(group.id)).members.filterNot { it.isSelf }) {
            val location = assertNotNull(peer.lastKnownLocation)
            val distance = GeoMath.distanceMeters(fence.center, location)
            assertTrue(
                distance < fence.radiusMeters * 2.5,
                "${peer.displayName} drifted to $distance m",
            )
        }
    }

    @Test
    fun forgetting_a_group_does_not_disturb_the_store() {
        val (store, simulator, _) = fixture()
        store.completeOnboarding("Harshit")
        val group = store.createGroup("Family", fence, addDemoMembers = true)
        simulator.tick(2.0)
        val before = store.current

        simulator.forgetGroup(group.id)

        assertEquals(before, store.current)
    }
}
