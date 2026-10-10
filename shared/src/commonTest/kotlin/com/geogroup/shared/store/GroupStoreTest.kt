package com.geogroup.shared.store

import com.geogroup.shared.DELHI
import com.geogroup.shared.TEST_EPOCH
import com.geogroup.shared.geo.GeoMath
import com.geogroup.shared.invite.InviteCodes
import com.geogroup.shared.model.ActivityKind
import com.geogroup.shared.model.AlertSeverity
import com.geogroup.shared.model.Coordinate
import com.geogroup.shared.model.FenceState
import com.geogroup.shared.model.Geofence
import com.geogroup.shared.model.TrackingGroup
import com.geogroup.shared.platform.FixedTimeProvider
import com.geogroup.shared.platform.InMemoryKeyValueStore
import com.geogroup.shared.sampleAt
import com.geogroup.shared.testStore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupStoreTest {

    private val fence = Geofence(center = DELHI, radiusMeters = 300.0, label = "Home")

    private fun GroupStore.onboardedGroup(addDemoMembers: Boolean = true): TrackingGroup {
        completeOnboarding("Harshit")
        return createGroup(name = "Family", geofence = fence, addDemoMembers = addDemoMembers)
    }

    /** Plants the member firmly inside so the next exit is a real transition. */
    private fun GroupStore.settleInside(memberId: String) {
        recordLocation(memberId, sampleAt(fence.center, distanceFromCenter = 20.0))
    }

    // ------------------------------------------------------- group creation

    @Test
    fun creating_a_group_adds_self_and_a_usable_invite_code() {
        val store = testStore()
        val group = store.onboardedGroup(addDemoMembers = false)

        val self = assertNotNull(group.selfMember, "creator must be a member")
        assertEquals("Harshit", self.displayName)
        assertEquals(group.ownerMemberId, self.id)
        assertTrue(InviteCodes.isValid(group.inviteCode))
        assertEquals(1, group.memberCount)
    }

    @Test
    fun the_creator_starts_in_an_unknown_fence_state() {
        // Assuming the creator is inside would let their first real GPS fix fire a
        // phantom "left the zone" alert at the whole group.
        val store = testStore()
        val group = store.onboardedGroup()
        assertEquals(FenceState.UNKNOWN, group.selfMember?.fenceState)
    }

    @Test
    fun creating_a_group_records_it_in_the_activity_feed() {
        val store = testStore()
        val group = store.onboardedGroup()

        val entry = store.current.activity.first()
        assertEquals(ActivityKind.GROUP_CREATED, entry.kind)
        assertEquals(group.id, entry.groupId)
        assertTrue(entry.actorIsSelf)
    }

    @Test
    fun demo_members_are_placed_inside_the_fence() {
        val store = testStore()
        val group = store.onboardedGroup()
        val peers = group.members.filterNot { it.isSelf }

        assertEquals(3, peers.size)
        for (peer in peers) {
            val location = assertNotNull(peer.lastKnownLocation, "${peer.displayName} has no position")
            val distance = GeoMath.distanceMeters(fence.center, location)
            assertTrue(distance < fence.radiusMeters, "${peer.displayName} was placed outside the fence")
            assertEquals(FenceState.INSIDE, peer.fenceState)
        }
    }

    @Test
    fun invite_codes_are_unique_across_groups() {
        val store = testStore()
        store.completeOnboarding("Harshit")
        val codes = (1..25).map {
            store.createGroup("Group $it", fence, addDemoMembers = false).inviteCode
        }
        assertEquals(codes.size, codes.toSet().size, "duplicate invite code issued")
    }

    // ------------------------------------------------ the core requirement

    @Test
    fun when_a_member_leaves_the_fence_the_other_members_are_notified() {
        val store = testStore()
        val group = store.onboardedGroup()
        val peer = group.members.first { !it.isSelf }
        val self = assertNotNull(group.selfMember)

        val alerts = store.recordLocation(
            peer.id,
            sampleAt(fence.center, distanceFromCenter = 500.0),
        )

        // 1. This device is told.
        assertEquals(1, alerts.size)
        val alert = alerts.single()
        assertEquals(AlertSeverity.BREACH, alert.severity)
        assertEquals(peer.id, alert.subjectMemberId)
        assertTrue(alert.title.contains(peer.displayName), "title must name who left: ${alert.title}")
        assertTrue(alert.title.contains("Home"), "title must name the zone: ${alert.title}")

        // 2. The fan-out is recorded, and covers everyone except the person who left.
        val entry = store.current.activity.first()
        assertEquals(ActivityKind.EXITED_FENCE, entry.kind)
        assertEquals(peer.id, entry.actorMemberId)
        assertEquals(group.memberCount - 1, entry.notifiedMemberIds.size)
        assertContains(entry.notifiedMemberIds, self.id)
        assertTrue(peer.id !in entry.notifiedMemberIds, "the member who left must not notify themselves")

        // 3. The member's own state moved.
        val updated = assertNotNull(store.current.group(group.id)?.member(peer.id))
        assertEquals(FenceState.OUTSIDE, updated.fenceState)
        assertEquals(200.0, assertNotNull(updated.signedDistanceFromEdgeMeters), absoluteTolerance = 2.0)
        assertEquals(200.0, assertNotNull(entry.distanceOutsideMeters), absoluteTolerance = 2.0)
    }

    @Test
    fun my_own_exit_notifies_the_group_and_gives_me_a_receipt() {
        val store = testStore()
        val group = store.onboardedGroup()
        val self = assertNotNull(group.selfMember)
        store.settleInside(self.id)

        val alerts = store.recordLocation(self.id, sampleAt(fence.center, distanceFromCenter = 900.0))

        val alert = alerts.single()
        assertEquals(AlertSeverity.SELF_CONFIRMATION, alert.severity)
        assertTrue(alert.body.contains("3 members notified"), "unexpected receipt: ${alert.body}")

        val entry = store.current.activity.first()
        assertEquals(ActivityKind.EXITED_FENCE, entry.kind)
        assertTrue(entry.actorIsSelf)
        assertEquals(3, entry.notifiedMemberIds.size)
    }

    @Test
    fun leaving_a_group_of_one_says_nobody_was_notified() {
        val store = testStore()
        val group = store.onboardedGroup(addDemoMembers = false)
        val self = assertNotNull(group.selfMember)
        store.settleInside(self.id)

        val alert = store.recordLocation(self.id, sampleAt(fence.center, 900.0)).single()
        assertTrue(alert.body.contains("nobody was notified"), "unexpected body: ${alert.body}")
    }

    @Test
    fun a_members_return_notifies_the_group_as_information_not_alarm() {
        val store = testStore()
        val group = store.onboardedGroup()
        val peer = group.members.first { !it.isSelf }

        store.recordLocation(peer.id, sampleAt(fence.center, 500.0))
        val alerts = store.recordLocation(peer.id, sampleAt(fence.center, 100.0))

        val alert = alerts.single()
        assertEquals(AlertSeverity.INFO, alert.severity)
        assertEquals(ActivityKind.RETURNED_TO_FENCE, store.current.activity.first().kind)
    }

    @Test
    fun my_own_return_does_not_notify_me() {
        val store = testStore()
        val group = store.onboardedGroup()
        val self = assertNotNull(group.selfMember)

        store.settleInside(self.id)
        store.recordLocation(self.id, sampleAt(fence.center, 900.0))
        val alerts = store.recordLocation(self.id, sampleAt(fence.center, 50.0))

        assertTrue(alerts.isEmpty(), "no one needs a push about their own arrival")
        // The group is still told, even though this device stays quiet.
        assertEquals(ActivityKind.RETURNED_TO_FENCE, store.current.activity.first().kind)
    }

    @Test
    fun a_first_fix_outside_the_fence_notifies_nobody() {
        val store = testStore()
        val group = store.onboardedGroup()
        val self = assertNotNull(group.selfMember)

        // Cold start while already away from home. Not an exit.
        val alerts = store.recordLocation(self.id, sampleAt(fence.center, 4_000.0))

        assertTrue(alerts.isEmpty())
        assertEquals(FenceState.OUTSIDE, store.current.group(group.id)?.member(self.id)?.fenceState)
        assertTrue(store.current.activity.none { it.kind == ActivityKind.EXITED_FENCE })
    }

    @Test
    fun jitter_on_the_boundary_does_not_spam_the_group() {
        val store = testStore()
        val group = store.onboardedGroup()
        val peer = group.members.first { !it.isSelf }
        store.settleInside(peer.id)

        var alertCount = 0
        for (distance in listOf(295.0, 305.0, 299.0, 311.0, 292.0, 309.0, 301.0)) {
            alertCount += store.recordLocation(peer.id, sampleAt(fence.center, distance)).size
        }

        assertEquals(0, alertCount, "boundary jitter produced $alertCount notifications")
    }

    @Test
    fun a_breach_in_a_group_i_am_not_in_is_not_my_business() {
        val store = testStore()
        store.completeOnboarding("Harshit")
        val other = store.createJoinableDemoGroup(near = DELHI)
        val peer = other.members.first()

        val alerts = store.recordLocation(
            peer.id,
            sampleAt(other.geofence.center, other.geofence.radiusMeters + 400.0),
        )

        assertTrue(alerts.isEmpty(), "should not alert about a group this device has not joined")
        // Still recorded, so joining later does not lose the history.
        assertEquals(ActivityKind.EXITED_FENCE, store.current.activity.first().kind)
    }

    @Test
    fun one_fix_updates_every_group_the_member_belongs_to() {
        val store = testStore()
        store.completeOnboarding("Harshit")
        val home = store.createGroup("Home", fence, addDemoMembers = false)
        val work = store.createGroup(
            "Work",
            Geofence(center = DELHI, radiusMeters = 1_000.0, label = "Office"),
            addDemoMembers = false,
        )
        val selfId = assertNotNull(home.selfMember).id

        store.settleInside(selfId)
        // 600 m clears the 300 m fence but is still inside the 1000 m one.
        store.recordLocation(selfId, sampleAt(fence.center, 600.0))

        assertEquals(FenceState.OUTSIDE, store.current.group(home.id)?.member(selfId)?.fenceState)
        assertEquals(FenceState.INSIDE, store.current.group(work.id)?.member(selfId)?.fenceState)
    }

    @Test
    fun a_fix_for_an_unknown_member_changes_nothing() {
        val store = testStore()
        store.onboardedGroup()
        val before = store.current

        val alerts = store.recordLocation("member_nope", sampleAt(fence.center, 5_000.0))

        assertTrue(alerts.isEmpty())
        assertEquals(before, store.current)
    }

    // ------------------------------------------------------------- joining

    @Test
    fun a_group_can_be_joined_with_a_messily_typed_code() {
        val store = testStore()
        store.completeOnboarding("Harshit")
        val target = store.createJoinableDemoGroup(near = DELHI)

        val outcome = store.joinGroup(InviteCodes.formatForDisplay(target.inviteCode).lowercase())

        val joined = assertNotNull(outcome.group, "expected join to succeed, got ${outcome.failure}")
        assertNull(outcome.failure)
        assertTrue(joined.members.any { it.isSelf })
        assertEquals(FenceState.UNKNOWN, joined.selfMember?.fenceState)
    }

    @Test
    fun joining_notifies_the_members_who_were_already_there() {
        val store = testStore()
        store.completeOnboarding("Harshit")
        val target = store.createJoinableDemoGroup(near = DELHI)
        val existingIds = target.members.map { it.id }

        store.joinGroup(target.inviteCode)

        val entry = store.current.activity.first()
        assertEquals(ActivityKind.MEMBER_JOINED, entry.kind)
        assertEquals(existingIds.toSet(), entry.notifiedMemberIds.toSet())
    }

    @Test
    fun join_failures_are_distinguishable() {
        val store = testStore()
        store.completeOnboarding("Harshit")
        val target = store.createJoinableDemoGroup(near = DELHI)

        assertEquals(JoinFailure.MALFORMED_CODE, store.joinGroup("nope").failure)
        assertEquals(JoinFailure.MALFORMED_CODE, store.joinGroup("").failure)
        // Well-formed but issued by nobody. "ZZZZZZ" is valid Crockford.
        assertEquals(JoinFailure.UNKNOWN_CODE, store.joinGroup("ZZZZZZ").failure)

        assertTrue(store.joinGroup(target.inviteCode).isSuccess)
        assertEquals(JoinFailure.ALREADY_A_MEMBER, store.joinGroup(target.inviteCode).failure)
    }

    @Test
    fun joined_groups_excludes_groups_only_known_locally() {
        val store = testStore()
        store.completeOnboarding("Harshit")
        val mine = store.createGroup("Family", fence, addDemoMembers = false)
        store.createJoinableDemoGroup(near = DELHI)

        assertEquals(2, store.current.groups.size)
        assertEquals(listOf(mine.id), store.current.joinedGroups.map { it.id })
    }

    // ------------------------------------------------- membership lifecycle

    @Test
    fun leaving_a_group_removes_me_but_keeps_the_group_for_everyone_else() {
        val store = testStore()
        val group = store.onboardedGroup()

        store.leaveGroup(group.id)

        val remaining = assertNotNull(store.current.group(group.id))
        assertEquals(3, remaining.memberCount)
        assertTrue(remaining.members.none { it.isSelf })
        assertEquals(ActivityKind.MEMBER_LEFT_GROUP, store.current.activity.first().kind)
    }

    @Test
    fun the_last_member_leaving_deletes_the_group() {
        val store = testStore()
        val group = store.onboardedGroup(addDemoMembers = false)

        store.leaveGroup(group.id)

        assertNull(store.current.group(group.id))
    }

    @Test
    fun deleting_a_group_also_discards_its_history() {
        val store = testStore()
        val group = store.onboardedGroup()
        val peer = group.members.first { !it.isSelf }
        store.recordLocation(peer.id, sampleAt(fence.center, 600.0))
        assertTrue(store.current.activity.isNotEmpty())

        store.deleteGroup(group.id)

        assertNull(store.current.group(group.id))
        assertTrue(store.current.activity.none { it.groupId == group.id })
    }

    // ------------------------------------------------------ fence editing

    @Test
    fun moving_the_fence_does_not_fire_alerts_about_the_old_one() {
        val store = testStore()
        val group = store.onboardedGroup()
        val peer = group.members.first { !it.isSelf }
        store.settleInside(peer.id)

        // Re-centre 10 km away. Everyone is now nominally "outside", but nobody moved.
        val movedCenter = GeoMath.offset(DELHI, 10_000.0, 90.0)
        store.updateGeofence(
            group.id,
            Geofence(center = movedCenter, radiusMeters = 300.0, label = "New Home"),
        )

        assertEquals(FenceState.UNKNOWN, store.current.group(group.id)?.member(peer.id)?.fenceState)

        // The next fix adopts a state silently instead of screaming about an exit.
        val alerts = store.recordLocation(peer.id, sampleAt(fence.center, 20.0))
        assertTrue(alerts.isEmpty(), "re-centring the fence must not alert: $alerts")
        assertEquals(FenceState.OUTSIDE, store.current.group(group.id)?.member(peer.id)?.fenceState)
    }

    @Test
    fun editing_the_fence_is_recorded_and_announced() {
        val store = testStore()
        val group = store.onboardedGroup()

        store.updateGeofence(
            group.id,
            Geofence(center = DELHI, radiusMeters = 800.0, label = "Wider Home"),
        )

        val updated = assertNotNull(store.current.group(group.id))
        assertEquals(800.0, updated.geofence.radiusMeters)
        assertEquals("Wider Home", updated.geofence.label)

        val entry = store.current.activity.first()
        assertEquals(ActivityKind.FENCE_UPDATED, entry.kind)
        assertEquals(3, entry.notifiedMemberIds.size)
    }

    // -------------------------------------------------------- persistence

    @Test
    fun state_survives_a_restart() {
        val keyValueStore = InMemoryKeyValueStore()
        val first = testStore(keyValueStore = keyValueStore)
        val group = first.onboardedGroup()
        val peer = group.members.first { !it.isSelf }
        first.recordLocation(peer.id, sampleAt(fence.center, 600.0))

        // A brand-new store over the same storage is what a cold launch looks like.
        val second = testStore(keyValueStore = keyValueStore)

        assertEquals(first.current, second.current)
        assertEquals("Harshit", second.current.profile?.displayName)
        assertEquals(FenceState.OUTSIDE, second.current.group(group.id)?.member(peer.id)?.fenceState)
        assertEquals(group.inviteCode, second.current.group(group.id)?.inviteCode)
    }

    @Test
    fun corrupt_storage_is_discarded_rather_than_crashing() {
        val keyValueStore = InMemoryKeyValueStore(
            mapOf(GroupStore.SNAPSHOT_KEY to "{ this is not json"),
        )
        val store = testStore(keyValueStore = keyValueStore)

        assertEquals(AppSnapshot(), store.current)
    }

    @Test
    fun resetting_clears_both_memory_and_storage() {
        val keyValueStore = InMemoryKeyValueStore()
        val store = testStore(keyValueStore = keyValueStore)
        store.onboardedGroup()

        store.reset()

        assertEquals(AppSnapshot(), store.current)
        assertNull(keyValueStore.read(GroupStore.SNAPSHOT_KEY))
        assertEquals(AppSnapshot(), testStore(keyValueStore = keyValueStore).current)
    }

    // ------------------------------------------------------------ identity

    @Test
    fun renaming_keeps_my_id_and_updates_my_name_everywhere() {
        val store = testStore()
        val group = store.onboardedGroup()
        val originalId = assertNotNull(group.selfMember).id

        val renamed = store.completeOnboarding("Harshit K")

        assertEquals(originalId, renamed.memberId, "member id must be stable across renames")
        assertEquals("Harshit K", store.current.group(group.id)?.selfMember?.displayName)
    }

    @Test
    fun blank_and_overlong_names_are_sanitised() {
        val store = testStore()
        assertEquals("Me", store.completeOnboarding("   ").displayName)
        assertEquals(40, store.completeOnboarding("x".repeat(100)).displayName.length)
    }

    @Test
    fun creating_a_group_before_onboarding_still_works() {
        // The UI gates this, but a crash here would be a poor way to enforce it.
        val store = testStore()
        val group = store.createGroup("Orphan", fence, addDemoMembers = false)
        assertEquals("Me", group.selfMember?.displayName)
        assertNotNull(store.current.profile)
    }

    // -------------------------------------------------------- activity feed

    @Test
    fun the_activity_feed_is_newest_first_and_bounded() {
        val store = testStore(time = FixedTimeProvider(TEST_EPOCH))
        val group = store.onboardedGroup()
        val peer = group.members.first { !it.isSelf }

        var timestamp = TEST_EPOCH
        // Alternating in/out, so every step is a transition and earns a feed entry.
        // Comfortably more than the cap, to prove eviction actually happens.
        val steps = AppSnapshot.ACTIVITY_LIMIT + 60
        repeat(steps) { index ->
            timestamp += 60_000
            val distance = if (index % 2 == 0) 600.0 else 50.0
            store.recordLocation(
                peer.id,
                sampleAt(fence.center, distance, timestamp = timestamp),
            )
        }

        val activity = store.current.activity
        assertEquals(AppSnapshot.ACTIVITY_LIMIT, activity.size)
        assertEquals(
            activity.map { it.atEpochMillis }.sortedDescending(),
            activity.map { it.atEpochMillis },
            "activity feed must be newest-first",
        )
        // The oldest entry must have been evicted, not merely pushed down.
        assertTrue(
            activity.none { it.kind == ActivityKind.GROUP_CREATED },
            "the oldest entries should have aged out of the feed",
        )
    }

    @Test
    fun a_joinable_demo_group_does_not_contain_me() {
        val store = testStore()
        store.completeOnboarding("Harshit")
        val group = store.createJoinableDemoGroup(near = Coordinate(19.0760, 72.8777))

        assertTrue(group.members.none { it.isSelf })
        assertTrue(group.members.isNotEmpty())
        assertTrue(InviteCodes.isValid(group.inviteCode))
    }
}
