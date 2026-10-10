package com.geogroup.shared.alert

import com.geogroup.shared.model.AlertSeverity
import com.geogroup.shared.model.BreachAlert
import com.geogroup.shared.model.Member
import com.geogroup.shared.model.TrackingGroup
import kotlin.math.roundToInt

/** Notification copy, kept here so it is covered by the same tests as the detection. */
internal object AlertFactory {

    /** Sent to every other member when someone leaves the fence. */
    fun peerExited(
        alertId: String,
        group: TrackingGroup,
        subject: Member,
        distanceOutsideMeters: Double,
        atEpochMillis: Long,
    ): BreachAlert = BreachAlert(
        id = alertId,
        groupId = group.id,
        groupName = group.name,
        subjectMemberId = subject.id,
        subjectDisplayName = subject.displayName,
        severity = AlertSeverity.BREACH,
        title = "${subject.displayName} left ${group.geofence.label}",
        body = "${formatDistance(distanceOutsideMeters)} outside the ${group.name} zone.",
        atEpochMillis = atEpochMillis,
    )

    fun peerReturned(
        alertId: String,
        group: TrackingGroup,
        subject: Member,
        atEpochMillis: Long,
    ): BreachAlert = BreachAlert(
        id = alertId,
        groupId = group.id,
        groupName = group.name,
        subjectMemberId = subject.id,
        subjectDisplayName = subject.displayName,
        severity = AlertSeverity.INFO,
        title = "${subject.displayName} is back inside ${group.geofence.label}",
        body = "Back within the ${group.name} zone.",
        atEpochMillis = atEpochMillis,
    )

    /** Shown to the person who left: a receipt that the group was told. */
    fun selfExited(
        alertId: String,
        group: TrackingGroup,
        self: Member,
        distanceOutsideMeters: Double,
        notifiedCount: Int,
        atEpochMillis: Long,
    ): BreachAlert = BreachAlert(
        id = alertId,
        groupId = group.id,
        groupName = group.name,
        subjectMemberId = self.id,
        subjectDisplayName = self.displayName,
        severity = AlertSeverity.SELF_CONFIRMATION,
        title = "You left ${group.geofence.label}",
        body = if (notifiedCount == 0) {
            "No one else is in ${group.name} yet, so nobody was notified."
        } else {
            "${formatDistance(distanceOutsideMeters)} outside — " +
                "${notifiedCount} ${if (notifiedCount == 1) "member" else "members"} notified."
        },
        atEpochMillis = atEpochMillis,
    )

    /** Metres below 1 km, otherwise one decimal of a kilometre. */
    fun formatDistance(meters: Double): String {
        val absolute = if (meters < 0.0) 0.0 else meters
        return if (absolute < 1000.0) {
            "${absolute.roundToInt()} m"
        } else {
            val km = absolute / 1000.0
            val tenths = (km * 10.0).roundToInt()
            "${tenths / 10}.${tenths % 10} km"
        }
    }
}
