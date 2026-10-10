import Shared
import SwiftUI
import UIKit

struct SettingsView: View {

    @Environment(AppModel.self) private var model
    @State private var name = ""
    @State private var showingResetConfirm = false

    var body: some View {
        NavigationStack {
            Form {
                Section("You") {
                    TextField("Your name", text: $name)
                        .textInputAutocapitalization(.words)
                        .onSubmit(saveName)
                    Button("Save name", action: saveName)
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty
                                  || name == model.displayName)
                }

                Section {
                    statusRow(
                        "Location",
                        value: locationStatusText,
                        isGood: model.hasBackgroundLocation
                    )
                    statusRow(
                        "Notifications",
                        value: model.needsNotificationPermission ? "Not allowed" : "Allowed",
                        isGood: !model.needsNotificationPermission
                    )
                    Button("Open iOS Settings", systemImage: "arrow.up.forward.app") {
                        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                        UIApplication.shared.open(url)
                    }
                } header: {
                    Text("Permissions")
                } footer: {
                    Text("Background location is what lets a zone exit reach your group while the app is closed. With \"While Using\", detection only happens when GeoGroup is open.")
                }

                Section {
                    Toggle("Simulate members moving", isOn: Binding(
                        get: { model.isSimulating },
                        set: { _ in model.toggleSimulation() }
                    ))
                } header: {
                    Text("Demo")
                } footer: {
                    Text("There is no server in this build, so other members are simulated. Only their GPS is fabricated: detection and notifications are the real implementation.")
                }

                Section {
                    LabeledContent("Groups", value: "\(model.groups.count)")
                    LabeledContent("Activity entries", value: "\(model.activity.count)")
                    Button("Erase all data", systemImage: "trash", role: .destructive) {
                        showingResetConfirm = true
                    }
                } header: {
                    Text("Data")
                }
            }
            .navigationTitle("Settings")
            .onAppear { name = model.displayName }
            .confirmationDialog(
                "Erase everything?",
                isPresented: $showingResetConfirm,
                titleVisibility: .visible
            ) {
                Button("Erase", role: .destructive) { model.resetAllData() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Your identity, groups and history are removed from this device.")
            }
        }
    }

    private var locationStatusText: String {
        if model.hasBackgroundLocation { return "Always" }
        if model.needsLocationPermission { return "Not allowed" }
        return "While using the app"
    }

    private func statusRow(_ title: String, value: String, isGood: Bool) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value).foregroundStyle(.secondary)
            Image(systemName: isGood ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                .foregroundStyle(isGood ? .green : .orange)
        }
    }

    private func saveName() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        model.completeOnboarding(name: trimmed)
    }
}
