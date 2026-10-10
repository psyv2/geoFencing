package com.geogroup.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class FenceState {
    // Distinct from INSIDE so the first fix can never look like a transition.
    UNKNOWN,
    INSIDE,
    OUTSIDE,
}

@Serializable
data class Member(
    val id: String,
    val displayName: String,
    val isSelf: Boolean,
    val fenceState: FenceState = FenceState.UNKNOWN,
    val lastKnownLocation: Coordinate? = null,
    /** Negative inside, positive outside. Null until the first fix. */
    val signedDistanceFromEdgeMeters: Double? = null,
    val lastSeenAtEpochMillis: Long? = null,
) {
    val initials: String
        get() = displayName.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifEmpty { "?" }
}
