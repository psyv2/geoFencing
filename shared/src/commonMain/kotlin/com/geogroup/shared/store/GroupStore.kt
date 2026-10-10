package com.geogroup.shared.store

import com.geogroup.shared.alert.AlertFactory
import com.geogroup.shared.core.Ids
import com.geogroup.shared.geo.FenceTransition
import com.geogroup.shared.geo.GeoMath
import com.geogroup.shared.geo.GeofenceEvaluator
import com.geogroup.shared.geo.SampleRejection
import com.geogroup.shared.invite.InviteCodes
import com.geogroup.shared.model.ActivityKind
import com.geogroup.shared.model.BreachAlert
import com.geogroup.shared.model.Coordinate
import com.geogroup.shared.model.FenceState
import com.geogroup.shared.model.Geofence
import com.geogroup.shared.model.GroupActivity
import com.geogroup.shared.model.LocationSample
import com.geogroup.shared.model.Member
import com.geogroup.shared.model.SelfProfile
import com.geogroup.shared.model.TrackingGroup
import com.geogroup.shared.platform.KeyValueStore
import com.geogroup.shared.platform.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Single source of truth for groups, membership and the breach fan-out.
 *
 * Every mutation is synchronous and produces a new [AppSnapshot]. There is no network
 * and persistence is a single key, so async buys nothing here.
 */
class GroupStore internal constructor(
    private val keyValueStore: KeyValueStore,
    private val time: TimeProvider,
    private val evaluator: GeofenceEvaluator,
    private val random: Random,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _snapshot = MutableStateFlow(restore())
    val snapshot: StateFlow<AppSnapshot> = _snapshot.asStateFlow()

    val current: AppSnapshot
        get() = _snapshot.value

    // ---------------------------------------------------------------- identity

    /** Creates or renames this install's identity. The member id is never regenerated. */
    fun completeOnboarding(displayName: String): SelfProfile {
        val name = sanitizeName(displayName, fallback = "Me")
        val now = time.nowEpochMillis()
        val profile = current.profile?.copy(displayName = name)
            ?: SelfProfile(
                memberId = Ids.generate(random, "member"),
                displayName = name,
                createdAtEpochMillis = now,
            )

        commit(
            current.copy(
                profile = profile,
                // Keep membership rows in step with the profile name.
                groups = current.groups.map { group ->
                    group.copy(
                        members = group.members.map { member ->
                            if (member.isSelf) member.copy(displayName = name) else member
                        },
                    )
                },
            ),
        )
        return profile
    }

    // ------------------------------------------------------------------ groups

    fun createGroup(
        name: String,
        geofence: Geofence,
        addDemoMembers: Boolean,
    ): TrackingGroup {
        val now = time.nowEpochMillis()
        val profile = ensureProfile(now)

        val self = Member(
            id = profile.memberId,
            displayName = profile.displayName,
            isSelf = true,
            // UNKNOWN, not INSIDE: the creator has no fix against this fence yet, and
            // assuming one would let their first real fix fire a phantom exit.
            fenceState = FenceState.UNKNOWN,
        )

        val peers = if (addDemoMembers) demoPeers(geofence, now) else emptyList()

        val group = TrackingGroup(
            id = Ids.generate(random, "group"),
            name = sanitizeName(name, fallback = "Untitled group"),
            inviteCode = freshInviteCode(),
            geofence = geofence,
            members = listOf(self) + peers,
            ownerMemberId = profile.memberId,
            createdAtEpochMillis = now,
        )

        commit(
            current.copy(
                groups = current.groups + group,
                activity = prepend(
                    GroupActivity(
                        id = Ids.generate(random, "act"),
                        groupId = group.id,
                        groupName = group.name,
                        actorMemberId = self.id,
                        actorDisplayName = self.displayName,
                        actorIsSelf = true,
                        kind = ActivityKind.GROUP_CREATED,
                        atEpochMillis = now,
                        coordinate = geofence.center,
                    ),
                ),
            ),
        )
        return group
    }

    fun joinGroup(rawCode: String): JoinOutcome {
        if (!InviteCodes.isValid(rawCode)) return JoinOutcome.failed(JoinFailure.MALFORMED_CODE)

        val code = InviteCodes.normalize(rawCode)
        val group = current.groups.firstOrNull { it.inviteCode == code }
            ?: return JoinOutcome.failed(JoinFailure.UNKNOWN_CODE)
        if (group.members.any { it.isSelf }) return JoinOutcome.failed(JoinFailure.ALREADY_A_MEMBER)

        val now = time.nowEpochMillis()
        val profile = ensureProfile(now)
        val self = Member(
            id = profile.memberId,
            displayName = profile.displayName,
            isSelf = true,
            fenceState = FenceState.UNKNOWN,
        )
        val existing = group.members
        val joined = group.copy(members = existing + self)

        commit(
            current.copy(
                groups = current.groups.map { if (it.id == group.id) joined else it },
                activity = prepend(
                    GroupActivity(
                        id = Ids.generate(random, "act"),
                        groupId = joined.id,
                        groupName = joined.name,
                        actorMemberId = self.id,
                        actorDisplayName = self.displayName,
                        actorIsSelf = true,
                        kind = ActivityKind.MEMBER_JOINED,
                        atEpochMillis = now,
                        notifiedMemberIds = existing.map { it.id },
                        notifiedDisplayNames = existing.map { it.displayName },
                    ),
                ),
            ),
        )
        return JoinOutcome.success(joined)
    }

    /** Removes this install, deleting the group once nobody is left. */
    fun leaveGroup(groupId: String) {
        val group = current.group(groupId) ?: return
        val self = group.selfMember ?: return
        val now = time.nowEpochMillis()
        val remaining = group.members.filterNot { it.isSelf }

        val nextGroups = if (remaining.isEmpty()) {
            current.groups.filterNot { it.id == groupId }
        } else {
            current.groups.map { if (it.id == groupId) it.copy(members = remaining) else it }
        }

        commit(
            current.copy(
                groups = nextGroups,
                activity = prepend(
                    GroupActivity(
                        id = Ids.generate(random, "act"),
                        groupId = group.id,
                        groupName = group.name,
                        actorMemberId = self.id,
                        actorDisplayName = self.displayName,
                        actorIsSelf = true,
                        kind = ActivityKind.MEMBER_LEFT_GROUP,
                        atEpochMillis = now,
                        notifiedMemberIds = remaining.map { it.id },
                        notifiedDisplayNames = remaining.map { it.displayName },
                    ),
                ),
            ),
        )
    }

    fun deleteGroup(groupId: String) {
        if (current.group(groupId) == null) return
        commit(
            current.copy(
                groups = current.groups.filterNot { it.id == groupId },
                activity = current.activity.filterNot { it.groupId == groupId },
            ),
        )
    }

    /**
     * Re-centres or resizes the fence, resetting every member to [FenceState.UNKNOWN].
     * A verdict about the old boundary says nothing about the new one.
     */
    fun updateGeofence(groupId: String, geofence: Geofence) {
        val group = current.group(groupId) ?: return
        val now = time.nowEpochMillis()
        val actor = group.selfMember
        val updated = group.copy(
            geofence = geofence,
            members = group.members.map {
                it.copy(fenceState = FenceState.UNKNOWN, signedDistanceFromEdgeMeters = null)
            },
        )

        commit(
            current.copy(
                groups = current.groups.map { if (it.id == groupId) updated else it },
                activity = prepend(
                    GroupActivity(
                        id = Ids.generate(random, "act"),
                        groupId = group.id,
                        groupName = group.name,
                        actorMemberId = actor?.id ?: group.ownerMemberId,
                        actorDisplayName = actor?.displayName ?: "Someone",
                        actorIsSelf = actor != null,
                        kind = ActivityKind.FENCE_UPDATED,
                        atEpochMillis = now,
                        coordinate = geofence.center,
                        notifiedMemberIds = group.members.filterNot { it.isSelf }.map { it.id },
                        notifiedDisplayNames = group.members.filterNot { it.isSelf }.map { it.displayName },
                    ),
                ),
            ),
        )
    }

    /** Builds a group this install is not in, so the invite flow works on one device. */
    fun createJoinableDemoGroup(near: Coordinate): TrackingGroup {
        val now = time.nowEpochMillis()
        val geofence = Geofence.clamped(center = near, radiusMeters = 400.0, label = "Base Camp")
        val group = TrackingGroup(
            id = Ids.generate(random, "group"),
            name = "Trek Crew",
            inviteCode = freshInviteCode(),
            geofence = geofence,
            members = demoPeers(geofence, now),
            ownerMemberId = "",
            createdAtEpochMillis = now,
        )
        commit(current.copy(groups = current.groups + group))
        return group
    }

    // ---------------------------------------------------------------- location

    /**
     * Feeds one fix for [memberId] into every group they belong to and returns the
     * alerts this device should raise. The only path that can change a fence state.
     */
    fun recordLocation(memberId: String, sample: LocationSample): List<BreachAlert> {
        val snapshot = current
        val selfMemberId = snapshot.profile?.memberId
        val alerts = mutableListOf<BreachAlert>()
        val newActivity = mutableListOf<GroupActivity>()
        var changed = false

        val nextGroups = snapshot.groups.map { group ->
            val member = group.member(memberId) ?: return@map group
            val evaluation = evaluator.evaluate(member.fenceState, group.geofence, sample)

            // An unusable fix tells us nothing, not even position.
            if (evaluation.rejection == SampleRejection.INVALID_FIX) return@map group

            val updatedMember = member.copy(
                fenceState = evaluation.state,
                lastKnownLocation = sample.coordinate,
                signedDistanceFromEdgeMeters = evaluation.signedDistanceFromEdgeMeters,
                lastSeenAtEpochMillis = sample.timestampEpochMillis,
            )
            if (updatedMember == member) return@map group
            changed = true

            val updatedGroup = group.copy(
                members = group.members.map { if (it.id == memberId) updatedMember else it },
            )

            if (evaluation.transition != FenceTransition.NONE) {
                val others = updatedGroup.members.filterNot { it.id == memberId }
                newActivity += GroupActivity(
                    id = Ids.generate(random, "act"),
                    groupId = updatedGroup.id,
                    groupName = updatedGroup.name,
                    actorMemberId = updatedMember.id,
                    actorDisplayName = updatedMember.displayName,
                    actorIsSelf = updatedMember.isSelf,
                    kind = if (evaluation.transition == FenceTransition.EXITED) {
                        ActivityKind.EXITED_FENCE
                    } else {
                        ActivityKind.RETURNED_TO_FENCE
                    },
                    atEpochMillis = sample.timestampEpochMillis,
                    coordinate = sample.coordinate,
                    distanceOutsideMeters = evaluation.signedDistanceFromEdgeMeters
                        .takeIf { evaluation.transition == FenceTransition.EXITED },
                    notifiedMemberIds = others.map { it.id },
                    notifiedDisplayNames = others.map { it.displayName },
                )

                alertFor(
                    group = updatedGroup,
                    subject = updatedMember,
                    transition = evaluation.transition,
                    signedDistanceFromEdgeMeters = evaluation.signedDistanceFromEdgeMeters,
                    selfMemberId = selfMemberId,
                    atEpochMillis = sample.timestampEpochMillis,
                )?.let { alerts += it }
            }

            updatedGroup
        }

        if (!changed) return emptyList()

        commit(
            snapshot.copy(
                groups = nextGroups,
                activity = prepend(newActivity, snapshot.activity),
            ),
        )
        return alerts
    }

    /** Decides what this device shows. Returns null when nothing should be raised. */
    private fun alertFor(
        group: TrackingGroup,
        subject: Member,
        transition: FenceTransition,
        signedDistanceFromEdgeMeters: Double,
        selfMemberId: String?,
        atEpochMillis: Long,
    ): BreachAlert? {
        val alertId = Ids.generate(random, "alert")
        val selfIsInGroup = selfMemberId != null && group.members.any { it.id == selfMemberId }

        return when {
            subject.isSelf && transition == FenceTransition.EXITED -> AlertFactory.selfExited(
                alertId = alertId,
                group = group,
                self = subject,
                distanceOutsideMeters = signedDistanceFromEdgeMeters,
                notifiedCount = group.members.count { it.id != subject.id },
                atEpochMillis = atEpochMillis,
            )

            subject.isSelf -> null

            // A group we hold locally but have not joined is not our business.
            !selfIsInGroup -> null

            transition == FenceTransition.EXITED -> AlertFactory.peerExited(
                alertId = alertId,
                group = group,
                subject = subject,
                distanceOutsideMeters = signedDistanceFromEdgeMeters,
                atEpochMillis = atEpochMillis,
            )

            else -> AlertFactory.peerReturned(
                alertId = alertId,
                group = group,
                subject = subject,
                atEpochMillis = atEpochMillis,
            )
        }
    }

    // ------------------------------------------------------------------- debug

    /** Wipes identity, groups and history. */
    fun reset() {
        keyValueStore.delete(SNAPSHOT_KEY)
        _snapshot.value = AppSnapshot()
    }

    // ----------------------------------------------------------------- helpers

    private fun ensureProfile(now: Long): SelfProfile {
        current.profile?.let { return it }
        val profile = SelfProfile(
            memberId = Ids.generate(random, "member"),
            displayName = "Me",
            createdAtEpochMillis = now,
        )
        commit(current.copy(profile = profile))
        return profile
    }

    private fun freshInviteCode(): String {
        val taken = current.groups.map { it.inviteCode }.toSet()
        // ~1e9 possible codes, so collisions are a formality. Bounded so it cannot hang.
        repeat(64) {
            val candidate = InviteCodes.generate(random)
            if (candidate !in taken) return candidate
        }
        return InviteCodes.generate(random)
    }

    private fun demoPeers(geofence: Geofence, now: Long): List<Member> =
        DEMO_PEER_NAMES.shuffled(random).take(3).map { name ->
            Member(
                id = Ids.generate(random, "member"),
                displayName = name,
                isSelf = false,
                // Placed by us, so INSIDE is a fact and a later exit is a real transition.
                fenceState = FenceState.INSIDE,
                lastKnownLocation = GeoMath.offset(
                    origin = geofence.center,
                    distanceMeters = geofence.radiusMeters * (0.15 + random.nextDouble() * 0.5),
                    bearingDegrees = random.nextDouble() * 360.0,
                ),
                signedDistanceFromEdgeMeters = null,
                lastSeenAtEpochMillis = now,
            )
        }

    private fun sanitizeName(raw: String, fallback: String): String =
        raw.trim().take(40).ifEmpty { fallback }

    private fun prepend(activity: GroupActivity): List<GroupActivity> =
        prepend(listOf(activity), current.activity)

    private fun prepend(
        fresh: List<GroupActivity>,
        existing: List<GroupActivity>,
    ): List<GroupActivity> =
        if (fresh.isEmpty()) existing
        else (fresh.sortedByDescending { it.atEpochMillis } + existing)
            .take(AppSnapshot.ACTIVITY_LIMIT)

    private fun commit(next: AppSnapshot) {
        _snapshot.value = next
        persist(next)
    }

    private fun persist(snapshot: AppSnapshot) {
        runCatching { keyValueStore.write(SNAPSHOT_KEY, json.encodeToString(snapshot)) }
    }

    private fun restore(): AppSnapshot {
        val stored = keyValueStore.read(SNAPSHOT_KEY) ?: return AppSnapshot()
        return runCatching { json.decodeFromString<AppSnapshot>(stored) }
            .getOrElse { AppSnapshot() }
    }

    internal companion object {
        const val SNAPSHOT_KEY: String = "com.geogroup.snapshot.v1"

        val DEMO_PEER_NAMES: List<String> = listOf(
            "Aanya", "Rohan", "Meera", "Kabir", "Ishaan", "Diya", "Vihaan", "Zoya",
        )
    }
}
