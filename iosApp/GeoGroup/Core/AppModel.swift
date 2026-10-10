import CoreLocation
import Foundation
import Observation
import Shared
import SwiftUI
import UserNotifications

/// Owns the shared SDK and the two platform services, and republishes shared state to
/// SwiftUI. Deliberately thin: it converts a `CLLocation` into primitives, hands them to
/// Kotlin, and mirrors whatever comes back. Decisions belong in the shared module.
@Observable
final class AppModel {

    // MARK: - Published state

    private(set) var snapshot: AppSnapshot
    private(set) var locationAuthorization: CLAuthorizationStatus
    private(set) var notificationAuthorization: UNAuthorizationStatus = .notDetermined
    private(set) var isSimulating = false

    private(set) var latestAlert: BreachAlert?

    // MARK: - Collaborators

    let notifications = NotificationService()
    let location = LocationService()

    @ObservationIgnored private let sdk: GeoGroupSdk
    @ObservationIgnored private var subscription: Subscription?
    @ObservationIgnored private var bannerDismissTask: Task<Void, Never>?

    // MARK: - Derived

    var isOnboarded: Bool { snapshot.isOnboarded }
    var displayName: String { snapshot.profile?.displayName ?? "" }
    var groups: [TrackingGroup] { snapshot.joinedGroups }
    var activity: [GroupActivity] { snapshot.activity }

    var groupsBeyondRegionLimit: Int {
        max(0, groups.count - LocationService.maxMonitoredRegions)
    }

    var needsLocationPermission: Bool {
        locationAuthorization == .notDetermined
            || locationAuthorization == .denied
            || locationAuthorization == .restricted
    }

    /// Without Always, nothing is detected once the phone is in a pocket.
    var hasBackgroundLocation: Bool { locationAuthorization == .authorizedAlways }

    var needsNotificationPermission: Bool {
        notificationAuthorization != .authorized && notificationAuthorization != .provisional
    }

    init() {
        let notifications = self.notifications
        let sdk = GeoGroupSdk.companion.create(alertPresenter: notifications)

        self.sdk = sdk
        self.snapshot = sdk.snapshot
        self.locationAuthorization = location.authorizationStatus

        wire()
    }

    deinit {
        subscription?.cancel()
        sdk.close()
    }

    private func wire() {
        subscription = sdk.observeSnapshot { [weak self] snapshot in
            // Delivered on the main queue by the SDK's coroutine scope.
            MainActor.assumeIsolated {
                guard let self else { return }
                self.snapshot = snapshot
                self.location.syncMonitoredRegions(for: snapshot.joinedGroups)
            }
        }

        location.onLocation = { [weak self] location in
            MainActor.assumeIsolated { self?.ingest(location) }
        }

        location.onRegionEvent = { groupId, didExit in
            // Woken by a boundary crossing. The verdict comes from the fresh fix.
            print("note: region \(didExit ? "exit" : "entry") reported for group \(groupId)")
        }

        location.onAuthorizationChange = { [weak self] status in
            MainActor.assumeIsolated {
                guard let self else { return }
                self.locationAuthorization = status
                self.location.syncMonitoredRegions(for: self.snapshot.joinedGroups)
            }
        }

        notifications.onAlert = { [weak self] alert in
            MainActor.assumeIsolated { self?.showBanner(alert) }
        }
    }

    // MARK: - Permissions

    func requestPermissions() {
        location.requestAuthorization()
        Task {
            await notifications.requestAuthorization()
            notificationAuthorization = notifications.authorizationStatus
        }
    }

    func refreshPermissionState() {
        locationAuthorization = location.authorizationStatus
        Task {
            await notifications.refreshAuthorizationStatus()
            notificationAuthorization = notifications.authorizationStatus
        }
    }

    func startTracking() {
        location.startUpdating()
        location.syncMonitoredRegions(for: snapshot.joinedGroups)
    }

    // MARK: - Location in

    private func ingest(_ location: CLLocation) {
        sdk.submitSelfLocation(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            horizontalAccuracyMeters: location.horizontalAccuracy,
            timestampEpochMillis: Int64(location.timestamp.timeIntervalSince1970 * 1000)
        )
    }

    var currentCoordinate: CLLocationCoordinate2D? {
        location.lastLocation?.coordinate
    }

    // MARK: - Identity

    func completeOnboarding(name: String) {
        sdk.completeOnboarding(displayName: name)
        requestPermissions()
    }

    // MARK: - Groups

    @discardableResult
    func createGroup(
        name: String,
        fenceLabel: String,
        center: CLLocationCoordinate2D,
        radiusMeters: Double,
        addDemoMembers: Bool
    ) -> TrackingGroup {
        let group = sdk.createGroup(
            name: name,
            fenceLabel: fenceLabel,
            centerLatitude: center.latitude,
            centerLongitude: center.longitude,
            radiusMeters: radiusMeters,
            addDemoMembers: addDemoMembers
        )
        startTracking()
        return group
    }

    func joinGroup(code: String) -> JoinOutcome {
        let outcome = sdk.joinGroup(inviteCode: code)
        if outcome.isSuccess { startTracking() }
        return outcome
    }

    func leaveGroup(_ group: TrackingGroup) {
        sdk.leaveGroup(groupId: group.id)
    }

    func deleteGroup(_ group: TrackingGroup) {
        sdk.deleteGroup(groupId: group.id)
    }

    func updateGeofence(
        groupId: String,
        fenceLabel: String,
        center: CLLocationCoordinate2D,
        radiusMeters: Double
    ) {
        sdk.updateGeofence(
            groupId: groupId,
            fenceLabel: fenceLabel,
            centerLatitude: center.latitude,
            centerLongitude: center.longitude,
            radiusMeters: radiusMeters
        )
    }

    @discardableResult
    func createJoinableDemoGroup(near coordinate: CLLocationCoordinate2D) -> TrackingGroup {
        sdk.createJoinableDemoGroup(
            nearLatitude: coordinate.latitude,
            nearLongitude: coordinate.longitude
        )
    }

    func group(id: String) -> TrackingGroup? {
        snapshot.group(id: id)
    }

    // MARK: - Simulation

    func toggleSimulation() {
        if isSimulating {
            sdk.stopSimulation()
        } else {
            sdk.startSimulation()
        }
        isSimulating = sdk.isSimulationRunning
    }

    func simulateExit(groupId: String, memberId: String) {
        sdk.simulateExit(groupId: groupId, memberId: memberId)
    }

    func simulateReturn(groupId: String, memberId: String) {
        sdk.simulateReturn(groupId: groupId, memberId: memberId)
    }

    // MARK: - Debug

    func resetAllData() {
        location.stopMonitoringAllRegions()
        sdk.resetAllData()
        notifications.clearDelivered()
        isSimulating = false
        latestAlert = nil
    }

    // MARK: - Banner

    private func showBanner(_ alert: BreachAlert) {
        // A fallback, not a duplicate: when notifications are allowed iOS shows its
        // own banner in the foreground and two stacked banners look broken.
        guard needsNotificationPermission else { return }
        latestAlert = alert
        bannerDismissTask?.cancel()
        bannerDismissTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled else { return }
            self?.latestAlert = nil
        }
    }

    func dismissBanner() {
        bannerDismissTask?.cancel()
        latestAlert = nil
    }
}
