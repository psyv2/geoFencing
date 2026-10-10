import Shared
import SwiftUI

@main
struct GeoGroupApp: App {

    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .task {
                    model.refreshPermissionState()
                    if model.isOnboarded { model.startTracking() }
                }
                .onChange(of: scenePhase) { _, phase in
                    // Region monitoring silently stops if Always is revoked in Settings.
                    if phase == .active {
                        model.refreshPermissionState()
                        if model.isOnboarded { model.startTracking() }
                    }
                }
        }
    }
}
