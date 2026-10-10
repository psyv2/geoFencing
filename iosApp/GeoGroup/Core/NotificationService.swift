import Foundation
import Shared
import UserNotifications

/// Swift half of `AlertPresenter`. Owns delivery and nothing else: whether to alert and
/// what it says is settled in the shared module.
final class NotificationService: NSObject, AlertPresenter, UNUserNotificationCenterDelegate {

    private(set) var recentAlerts: [BreachAlert] = []

    var onAlert: ((BreachAlert) -> Void)?

    private let center = UNUserNotificationCenter.current()

    override init() {
        super.init()
        center.delegate = self
    }

    private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined

    func refreshAuthorizationStatus() async {
        let settings = await center.notificationSettings()
        await MainActor.run { self.authorizationStatus = settings.authorizationStatus }
    }

    @discardableResult
    func requestAuthorization() async -> Bool {
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            await refreshAuthorizationStatus()
            return granted
        } catch {
            await refreshAuthorizationStatus()
            return false
        }
    }

    // MARK: - AlertPresenter

    /// Called from Kotlin on the main queue.
    func present(alert: BreachAlert) {
        recentAlerts.insert(alert, at: 0)
        if recentAlerts.count > 50 { recentAlerts.removeLast(recentAlerts.count - 50) }
        onAlert?(alert)
        deliver(alert)
    }

    private func deliver(_ alert: BreachAlert) {
        let content = UNMutableNotificationContent()
        content.title = alert.title
        content.body = alert.body
        content.sound = alert.severity == AlertSeverity.breach ? .defaultCritical : .default
        content.threadIdentifier = alert.groupId
        content.categoryIdentifier = "fence-transition"
        content.userInfo = [
            "groupId": alert.groupId,
            "memberId": alert.subjectMemberId,
            "severity": alert.severity.name,
        ]
        if alert.severity == AlertSeverity.breach {
            content.interruptionLevel = .timeSensitive
        }

        // nil trigger: deliver now.
        center.add(
            UNNotificationRequest(identifier: alert.id, content: content, trigger: nil),
            withCompletionHandler: nil
        )
    }

    func clearDelivered() {
        center.removeAllDeliveredNotifications()
        recentAlerts.removeAll()
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// iOS suppresses notifications in the foreground unless we opt in here, which is
    /// exactly when a teammate leaving the zone matters most.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }
}
