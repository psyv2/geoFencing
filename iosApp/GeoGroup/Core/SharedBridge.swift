import CoreLocation
import Foundation
import Shared
import SwiftUI

// Adapters over the Kotlin/Native API. Nullable primitives arrive boxed as
// `KotlinDouble`/`KotlinLong` and times as epoch millis; unwrapping here keeps the
// views free of `.doubleValue` noise.

// MARK: - SwiftUI identity

// Kotlin-owned types, so these conformances have to be declared on this side.
extension TrackingGroup: @retroactive Identifiable {}
extension Member: @retroactive Identifiable {}
extension GroupActivity: @retroactive Identifiable {}
extension BreachAlert: @retroactive Identifiable {}

// MARK: - Geometry

extension Coordinate {
    var clCoordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

extension CLLocationCoordinate2D {
    var sharedCoordinate: Coordinate {
        Coordinate(latitude: latitude, longitude: longitude)
    }
}

// MARK: - Members

extension Member {
    var signedDistanceFromEdge: Double? { signedDistanceFromEdgeMeters?.doubleValue }

    var lastSeenDate: Date? {
        guard let millis = lastSeenAtEpochMillis?.int64Value else { return nil }
        return Date(timeIntervalSince1970: Double(millis) / 1000)
    }

    var isOutside: Bool { fenceState == FenceState.outside }
    var isInside: Bool { fenceState == FenceState.inside }
    var hasFix: Bool { fenceState != FenceState.unknown }

    var statusText: String {
        guard let distance = signedDistanceFromEdge else { return "Waiting for location" }
        if isOutside {
            return "\(Self.format(distance)) outside"
        }
        return "\(Self.format(-distance)) inside"
    }

    var statusColor: Color {
        if isOutside { return .red }
        if isInside { return .green }
        return .secondary
    }

    static func format(_ meters: Double) -> String {
        let clamped = max(0, meters)
        if clamped < 1000 { return "\(Int(clamped.rounded())) m" }
        return String(format: "%.1f km", clamped / 1000)
    }
}

// MARK: - Groups

extension TrackingGroup {
    var sortedMembers: [Member] {
        // Self first, then whoever is outside.
        members.sorted { lhs, rhs in
            if lhs.isSelf != rhs.isSelf { return lhs.isSelf }
            if lhs.isOutside != rhs.isOutside { return lhs.isOutside }
            return lhs.displayName.localizedCaseInsensitiveCompare(rhs.displayName) == .orderedAscending
        }
    }

    var outsideCount: Int { members.filter(\.isOutside).count }

    var formattedInviteCode: String { InviteCodes.shared.formatForDisplay(code: inviteCode) }

    var createdDate: Date { Date(timeIntervalSince1970: Double(createdAtEpochMillis) / 1000) }
}

// MARK: - Activity

extension GroupActivity {
    var date: Date { Date(timeIntervalSince1970: Double(atEpochMillis) / 1000) }

    var distanceOutside: Double? { distanceOutsideMeters?.doubleValue }

    var isBreach: Bool { kind == ActivityKind.exitedFence }

    var iconName: String {
        switch kind {
        case ActivityKind.exitedFence: return "figure.walk.departure"
        case ActivityKind.returnedToFence: return "figure.walk.arrival"
        case ActivityKind.memberJoined: return "person.badge.plus"
        case ActivityKind.memberLeftGroup: return "person.badge.minus"
        case ActivityKind.fenceUpdated: return "circle.dashed"
        default: return "flag"
        }
    }

    var tint: Color {
        switch kind {
        case ActivityKind.exitedFence: return .red
        case ActivityKind.returnedToFence: return .green
        case ActivityKind.fenceUpdated: return .orange
        default: return .accentColor
        }
    }

    var headline: String {
        let who = actorIsSelf ? "You" : actorDisplayName
        switch kind {
        case ActivityKind.exitedFence:
            let suffix = distanceOutside.map { " (\(Member.format($0)) out)" } ?? ""
            return "\(who) left \(groupName)\(suffix)"
        case ActivityKind.returnedToFence:
            return "\(who) returned to \(groupName)"
        case ActivityKind.memberJoined:
            return "\(who) joined \(groupName)"
        case ActivityKind.memberLeftGroup:
            return "\(who) left the \(groupName) group"
        case ActivityKind.groupCreated:
            return "\(who) created \(groupName)"
        case ActivityKind.fenceUpdated:
            return "\(who) moved the \(groupName) zone"
        default:
            return "\(who) — \(groupName)"
        }
    }

    /// Who was notified about this event.
    var notificationSummary: String? {
        guard !notifiedDisplayNames.isEmpty else {
            guard isBreach else { return nil }
            return "No one else to notify"
        }
        let names = notifiedDisplayNames
        if names.count <= 2 { return "Notified \(names.joined(separator: " and "))" }
        return "Notified \(names[0]), \(names[1]) and \(names.count - 2) more"
    }
}

// MARK: - Alerts

extension BreachAlert {
    var date: Date { Date(timeIntervalSince1970: Double(atEpochMillis) / 1000) }

    var tint: Color {
        switch severity {
        case AlertSeverity.breach: return .red
        case AlertSeverity.info: return .green
        default: return .accentColor
        }
    }
}

// MARK: - Join failures

extension JoinFailure {
    var message: String {
        switch self {
        case JoinFailure.malformedCode:
            return "An invite code is 6 characters, like TRE-K42."
        case JoinFailure.unknownCode:
            return "No group on this device uses that code."
        case JoinFailure.alreadyAMember:
            return "You are already in this group."
        default:
            return "That code could not be used."
        }
    }
}
