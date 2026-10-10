import Shared
import SwiftUI

struct JoinGroupView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var code = ""
    @State private var failureMessage: String?
    @State private var demoCode: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Invite code", text: $code)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .font(.title3.monospaced())
                        .onChange(of: code) { _, _ in failureMessage = nil }
                        .onSubmit(join)
                } header: {
                    Text("Enter a code")
                } footer: {
                    if let failureMessage {
                        Text(failureMessage).foregroundStyle(.red)
                    } else {
                        Text("Six characters. Dashes, spaces and lower case are fine.")
                    }
                }

                Section {
                    Button("Join group", action: join)
                        .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty)
                }

                Section {
                    if let demoCode {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Demo invite created")
                                .font(.subheadline.weight(.semibold))
                            Text(InviteCodes.shared.formatForDisplay(code: demoCode))
                                .font(.title2.monospaced().weight(.bold))
                                .textSelection(.enabled)
                            Text("Type it above to join, as if someone had sent it to you.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        Button("Create a demo invite near me", systemImage: "wand.and.stars", action: createDemoInvite)
                            .disabled(model.currentCoordinate == nil)
                    }
                } header: {
                    Text("No server in this build")
                } footer: {
                    Text(model.currentCoordinate == nil
                         ? "Waiting for a location fix before a demo zone can be placed."
                         : "Invites normally arrive from another device. This creates one locally.")
                }
            }
            .navigationTitle("Join a group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func join() {
        let outcome = model.joinGroup(code: code)
        if outcome.isSuccess {
            dismiss()
        } else {
            failureMessage = outcome.failure?.message ?? "That code could not be used."
        }
    }

    private func createDemoInvite() {
        guard let coordinate = model.currentCoordinate else { return }
        demoCode = model.createJoinableDemoGroup(near: coordinate).inviteCode
    }
}
