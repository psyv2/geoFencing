import MapKit
import Shared
import SwiftUI
import UIKit

struct GroupDetailView: View {

    let groupId: String

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var showingEditFence = false
    @State private var showingLeaveConfirm = false
    @State private var copiedCode = false

    private var group: TrackingGroup? { model.group(id: groupId) }

    var body: some View {
        if let group {
            content(for: group)
        } else {
            ContentUnavailableView(
                "Group unavailable",
                systemImage: "person.3.slash",
                description: Text("You are no longer a member of this group.")
            )
        }
    }

    private func content(for group: TrackingGroup) -> some View {
        List {
            Section {
                FenceMapView(geofence: group.geofence, members: group.members)
                    .frame(height: 260)
                    .listRowInsets(EdgeInsets())
            }

            Section("Zone") {
                LabeledContent("Name", value: group.geofence.label)
                LabeledContent("Radius", value: "\(Int(group.geofence.radiusMeters)) m")
                Button("Move or resize the zone", systemImage: "circle.dashed.inset.filled") {
                    showingEditFence = true
                }
            }

            Section {
                ForEach(group.sortedMembers) { member in
                    MemberRow(
                        member: member,
                        onSimulateExit: { model.simulateExit(groupId: group.id, memberId: member.id) },
                        onSimulateReturn: { model.simulateReturn(groupId: group.id, memberId: member.id) }
                    )
                }
            } header: {
                HStack {
                    Text("Members")
                    Spacer()
                    if group.outsideCount > 0 {
                        Text("\(group.outsideCount) outside").foregroundStyle(.red)
                    }
                }
            } footer: {
                Text("Use the menu on a member to move them across the boundary. This drives the same detection path as a real GPS fix.")
            }

            Section {
                Button {
                    UIPasteboard.general.string = group.inviteCode
                    copiedCode = true
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(group.formattedInviteCode)
                                .font(.title3.monospaced().weight(.bold))
                            Text(copiedCode ? "Copied" : "Tap to copy")
                                .font(.caption)
                                .foregroundStyle(copiedCode ? .green : .secondary)
                        }
                        Spacer()
                        Image(systemName: copiedCode ? "checkmark.circle.fill" : "doc.on.doc")
                            .foregroundStyle(copiedCode ? .green : .secondary)
                    }
                }
                .buttonStyle(.plain)
            } header: {
                Text("Invite code")
            }

            let groupActivity = model.activity.filter { $0.groupId == group.id }
            if !groupActivity.isEmpty {
                Section("Recent activity") {
                    ForEach(groupActivity.prefix(12)) { entry in
                        ActivityRow(entry: entry)
                    }
                }
            }

            Section {
                Toggle("Simulate members moving", isOn: Binding(
                    get: { model.isSimulating },
                    set: { _ in model.toggleSimulation() }
                ))
                Button("Leave group", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) {
                    showingLeaveConfirm = true
                }
            } footer: {
                Text("Demo members wander in and out of every zone on an accelerated clock.")
            }
        }
        .navigationTitle(group.name)
        .navigationBarTitleDisplayMode(.large)
        .sheet(isPresented: $showingEditFence) {
            EditFenceView(groupId: group.id)
        }
        .confirmationDialog(
            "Leave \(group.name)?",
            isPresented: $showingLeaveConfirm,
            titleVisibility: .visible
        ) {
            Button("Leave group", role: .destructive) {
                model.leaveGroup(group)
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You will stop being tracked against this zone. The group stays for everyone else.")
        }
        .onChange(of: copiedCode) { _, isCopied in
            guard isCopied else { return }
            Task {
                try? await Task.sleep(for: .seconds(2))
                copiedCode = false
            }
        }
    }
}
