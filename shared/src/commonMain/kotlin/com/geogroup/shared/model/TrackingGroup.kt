package com.geogroup.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackingGroup(
    val id: String,
    val name: String,
    val inviteCode: String,
    val geofence: Geofence,
    val members: List<Member>,
    val ownerMemberId: String,
    val createdAtEpochMillis: Long,
) {
    val selfMember: Member?
        get() = members.firstOrNull { it.isSelf }

    val memberCount: Int
        get() = members.size

    val membersOutside: List<Member>
        get() = members.filter { it.fenceState == FenceState.OUTSIDE }

    fun member(id: String): Member? = members.firstOrNull { it.id == id }
}
