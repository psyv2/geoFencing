package com.geogroup.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AlertSeverity {
    BREACH,
    INFO,

    /** Confirmation that your own exit was broadcast to the group. */
    SELF_CONFIRMATION,
}

/**
 * A fully-composed notification. The platform layer only delivers it; it never decides
 * whether to alert or what the text is.
 */
@Serializable
data class BreachAlert(
    val id: String,
    val groupId: String,
    val groupName: String,
    val subjectMemberId: String,
    val subjectDisplayName: String,
    val severity: AlertSeverity,
    val title: String,
    val body: String,
    val atEpochMillis: Long,
)
