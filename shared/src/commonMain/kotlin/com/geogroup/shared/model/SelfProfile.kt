package com.geogroup.shared.model

import kotlinx.serialization.Serializable

/** This install's identity, generated on first launch. Membership is keyed on [memberId]. */
@Serializable
data class SelfProfile(
    val memberId: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
)
