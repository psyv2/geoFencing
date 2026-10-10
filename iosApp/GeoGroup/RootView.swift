import Shared
import SwiftUI

struct RootView: View {

    @Environment(AppModel.self) private var model

    var body: some View {
        Group {
            if model.isOnboarded {
                MainTabView()
            } else {
                OnboardingView()
            }
        }
        .animation(.default, value: model.isOnboarded)
        .overlay(alignment: .top) {
            if let alert = model.latestAlert {
                AlertBanner(alert: alert) { model.dismissBanner() }
                    .padding(.horizontal)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.35), value: model.latestAlert?.id)
    }
}

struct MainTabView: View {

    @Environment(AppModel.self) private var model

    var body: some View {
        TabView {
            GroupListView()
                .tabItem { Label("Groups", systemImage: "person.3") }

            ActivityView()
                .tabItem { Label("Activity", systemImage: "list.bullet.rectangle") }
                .badge(model.activity.filter(\.isBreach).count)

            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }
}
