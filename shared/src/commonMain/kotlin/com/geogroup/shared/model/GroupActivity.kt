package com.geogroup.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityKind {
    GROUP_CREATED,
    MEMBER_JOINED,
    MEMBER_LEFT_GROUP,
    EXITED_FENCE,
    RETURNED_TO_FENCE,
    FENCE_UPDATED,
}

/** One entry in a group's history. [notifiedMemberIds] records who was alerted. */
@Serializable
data class GroupActivity(
    val id: String,
    val groupId: String,
    val groupName: String,
    val actorMemberId: String,
    val actorDisplayName: String,
    val actorIsSelf: Boolean,
    val kind: ActivityKind,
    val atEpochMillis: Long,
    val coordinate: Coordinate? = null,
    val distanceOutsideMeters: Double? = null,
    val notifiedMemberIds: List<String> = emptyList(),
    val notifiedDisplayNames: List<String> = emptyList(),
)
