package com.geogroup.shared.store

import com.geogroup.shared.model.GroupActivity
import com.geogroup.shared.model.SelfProfile
import com.geogroup.shared.model.TrackingGroup
import kotlinx.serialization.Serializable

/** The whole app's persisted state, in one immutable value. */
@Serializable
data class AppSnapshot(
    val profile: SelfProfile? = null,
    val groups: List<TrackingGroup> = emptyList(),
    /** Newest first, capped at [ACTIVITY_LIMIT]. */
    val activity: List<GroupActivity> = emptyList(),
) {
    val isOnboarded: Boolean
        get() = profile != null

    fun group(id: String): TrackingGroup? = groups.firstOrNull { it.id == id }

    /** Groups this install is actually a member of, not merely aware of. */
    val joinedGroups: List<TrackingGroup>
        get() = groups.filter { group -> group.members.any { it.isSelf } }

    companion object {
        const val ACTIVITY_LIMIT: Int = 200
    }
}

enum class JoinFailure {
    MALFORMED_CODE,
    UNKNOWN_CODE,
    ALREADY_A_MEMBER,
}

/**
 * Result of a join attempt. Two nullable halves rather than a sealed hierarchy, which
 * maps to a plain optional check in Swift.
 */
data class JoinOutcome(
    val group: TrackingGroup?,
    val failure: JoinFailure?,
) {
    val isSuccess: Boolean
        get() = group != null

    companion object {
        internal fun success(group: TrackingGroup) = JoinOutcome(group, null)
        internal fun failed(failure: JoinFailure) = JoinOutcome(null, failure)
    }
}
