import Shared
import SwiftUI

struct GroupListView: View {

    @Environment(AppModel.self) private var model
    @State private var showingCreate = false
    @State private var showingJoin = false

    var body: some View {
        NavigationStack {
            List {
                if model.needsLocationPermission || !model.hasBackgroundLocation || model.needsNotificationPermission {
                    Section {
                        permissionCards
                    }
                }

                if model.groupsBeyondRegionLimit > 0 {
                    Section {
                        Label(
                            "iOS monitors at most \(LocationService.maxMonitoredRegions) zones in the background. \(model.groupsBeyondRegionLimit) of your groups only update while the app is open.",
                            systemImage: "exclamationmark.circle"
                        )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                }

                if model.groups.isEmpty {
                    Section {
                        emptyState
                    }
                } else {
                    Section("Your groups") {
                        ForEach(model.groups) { group in
                            NavigationLink(value: group.id) {
                                GroupSummaryRow(group: group)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Groups")
            .navigationDestination(for: String.self) { groupId in
                GroupDetailView(groupId: groupId)
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("Create a group", systemImage: "plus.circle") { showingCreate = true }
                        Button("Join with a code", systemImage: "number") { showingJoin = true }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showingCreate) { CreateGroupView() }
            .sheet(isPresented: $showingJoin) { JoinGroupView() }
        }
    }

    @ViewBuilder private var permissionCards: some View {
        if model.needsLocationPermission {
            PermissionCard(
                iconName: "location.slash",
                title: "Location access needed",
                message: "Without it, GeoGroup cannot tell whether you are inside your group's zone.",
                actionTitle: "Grant access",
                action: model.requestPermissions
            )
        } else if !model.hasBackgroundLocation {
            PermissionCard(
                iconName: "location.circle",
                title: "Only tracking while open",
                message: "Set location to \"Always\" in Settings so your group is still notified when you leave a zone with the app closed.",
                actionTitle: "Ask again",
                action: model.requestPermissions
            )
        }

        if model.needsNotificationPermission {
            PermissionCard(
                iconName: "bell.slash",
                title: "Notifications are off",
                message: "Breaches are still recorded in Activity, but you will not be alerted when a member leaves.",
                actionTitle: "Enable notifications",
                action: model.requestPermissions
            )
        }
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "person.3.sequence")
                .font(.largeTitle)
                .foregroundStyle(.tertiary)
            Text("No groups yet")
                .font(.headline)
            Text("Create a group and draw a zone around it, or join one with an invite code.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            HStack {
                Button("Create") { showingCreate = true }
                    .buttonStyle(.borderedProminent)
                Button("Join") { showingJoin = true }
                    .buttonStyle(.bordered)
            }
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
    }
}

struct GroupSummaryRow: View {

    let group: TrackingGroup

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(group.name)
                    .font(.headline)
                Spacer()
                if group.outsideCount > 0 {
                    Text("\(group.outsideCount) outside")
                        .font(.caption2.weight(.bold))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(.red, in: Capsule())
                        .foregroundStyle(.white)
                }
            }

            HStack(spacing: 6) {
                Image(systemName: "circle.dashed")
                Text(group.geofence.label)
                Text("·")
                Text("\(Int(group.geofence.radiusMeters)) m")
                Text("·")
                Image(systemName: "person.2")
                Text("\(group.memberCount)")
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            // Whole-group state at a glance, without opening it.
            HStack(spacing: 4) {
                ForEach(group.sortedMembers.prefix(8)) { member in
                    Circle()
                        .fill(member.statusColor)
                        .frame(width: 7, height: 7)
                }
                if group.memberCount > 8 {
                    Text("+\(group.memberCount - 8)")
                        .font(.system(size: 9))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 2)
    }
}
