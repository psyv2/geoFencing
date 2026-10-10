import CoreLocation
import Foundation
import Shared

/// Everything CoreLocation, and nothing else.
///
/// Two paths feed the shared evaluator: continuous updates while the app is in use, and
/// `CLCircularRegion` monitoring, which is the only thing that can wake a suspended app.
/// Region events are treated as a wake-up only; the verdict still comes from a fresh fix
/// run through the shared evaluator.
final class LocationService: NSObject, CLLocationManagerDelegate {

    /// iOS will not monitor more regions than this for one app.
    static let maxMonitoredRegions = 20

    private let manager = CLLocationManager()

    var onLocation: ((CLLocation) -> Void)?

    var onRegionEvent: ((_ groupId: String, _ didExit: Bool) -> Void)?

    var onAuthorizationChange: ((CLAuthorizationStatus) -> Void)?

    private(set) var lastLocation: CLLocation?

    var authorizationStatus: CLAuthorizationStatus { manager.authorizationStatus }

    var isAuthorizedForBackground: Bool {
        manager.authorizationStatus == .authorizedAlways
    }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        // Fine enough to track movement toward an edge without reacting to GPS noise.
        manager.distanceFilter = 10
        manager.pausesLocationUpdatesAutomatically = true
        manager.activityType = .other
    }

    // MARK: - Permissions

    func requestAuthorization() {
        switch manager.authorizationStatus {
        case .notDetermined:
            // iOS only allows the Always upgrade after When-In-Use is granted.
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse:
            manager.requestAlwaysAuthorization()
        default:
            break
        }
    }

    // MARK: - Lifecycle

    func startUpdating() {
        guard manager.authorizationStatus == .authorizedAlways
            || manager.authorizationStatus == .authorizedWhenInUse
        else { return }

        manager.startUpdatingLocation()

        if manager.authorizationStatus == .authorizedAlways {
            manager.allowsBackgroundLocationUpdates = true
            // Survives termination, unlike standard updates.
            manager.startMonitoringSignificantLocationChanges()
        }
        manager.requestLocation()
    }

    func stopUpdating() {
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        manager.stopMonitoringSignificantLocationChanges()
    }

    func requestOneShotFix() {
        manager.requestLocation()
    }

    // MARK: - Region monitoring

    /// Reconciles monitored regions with the groups we care about. Keyed by group id, so
    /// a moved or resized fence replaces its predecessor rather than accumulating.
    func syncMonitoredRegions(for groups: [TrackingGroup]) {
        guard CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self),
              manager.authorizationStatus == .authorizedAlways
        else { return }

        let wanted = Dictionary(
            groups.prefix(Self.maxMonitoredRegions).map { ($0.id, $0) },
            uniquingKeysWith: { first, _ in first }
        )

        for region in manager.monitoredRegions {
            guard let circular = region as? CLCircularRegion else { continue }
            if let group = wanted[circular.identifier], matches(circular, group) {
                continue
            }
            manager.stopMonitoring(for: region)
        }

        let alreadyMonitored = Set(manager.monitoredRegions.map(\.identifier))
        for (id, group) in wanted where !alreadyMonitored.contains(id) {
            let region = CLCircularRegion(
                center: group.geofence.center.clCoordinate,
                radius: min(group.geofence.radiusMeters, manager.maximumRegionMonitoringDistance),
                identifier: id
            )
            region.notifyOnExit = true
            region.notifyOnEntry = true
            manager.startMonitoring(for: region)
        }

        if groups.count > Self.maxMonitoredRegions {
            // Surfaced in the UI too; the excess groups fall back to foreground tracking.
            print("warning: \(groups.count) groups exceeds the \(Self.maxMonitoredRegions)-region iOS limit")
        }
    }

    func stopMonitoringAllRegions() {
        for region in manager.monitoredRegions {
            manager.stopMonitoring(for: region)
        }
    }

    private func matches(_ region: CLCircularRegion, _ group: TrackingGroup) -> Bool {
        let center = group.geofence.center
        return abs(region.center.latitude - center.latitude) < 1e-6
            && abs(region.center.longitude - center.longitude) < 1e-6
            && abs(region.radius - group.geofence.radiusMeters) < 1.0
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        lastLocation = location
        onLocation?(location)
    }

    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        onRegionEvent?(region.identifier, true)
        // The OS event only says something happened. Let the shared evaluator decide
        // what it meant, from a fresh fix.
        manager.requestLocation()
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        onRegionEvent?(region.identifier, false)
        manager.requestLocation()
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        onAuthorizationChange?(manager.authorizationStatus)
        startUpdating()
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Transient failures are normal indoors and CoreLocation retries on its own.
        if let clError = error as? CLError, clError.code == .denied {
            stopUpdating()
        }
    }

    func locationManager(
        _ manager: CLLocationManager,
        monitoringDidFailFor region: CLRegion?,
        withError error: Error
    ) {
        print("warning: region monitoring failed for \(region?.identifier ?? "unknown"): \(error.localizedDescription)")
    }
}
