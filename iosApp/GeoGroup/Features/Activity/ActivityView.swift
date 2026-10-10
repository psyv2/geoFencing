import Shared
import SwiftUI

struct ActivityView: View {

    @Environment(AppModel.self) private var model
    @State private var breachesOnly = false

    private var entries: [GroupActivity] {
        breachesOnly ? model.activity.filter(\.isBreach) : model.activity
    }

    var body: some View {
        NavigationStack {
            Group {
                if model.activity.isEmpty {
                    ContentUnavailableView(
                        "Nothing has happened yet",
                        systemImage: "list.bullet.rectangle",
                        description: Text("Zone crossings, joins and zone edits will appear here.")
                    )
                } else {
                    List {
                        Section {
                            Toggle("Zone crossings only", isOn: $breachesOnly)
                                .font(.subheadline)
                        }
                        Section {
                            ForEach(entries) { entry in
                                ActivityRow(entry: entry)
                            }
                        } footer: {
                            Text("Each entry records who was notified.")
                        }
                    }
                }
            }
            .navigationTitle("Activity")
        }
    }
}

struct ActivityRow: View {

    let entry: GroupActivity

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: entry.iconName)
                .font(.footnote)
                .foregroundStyle(entry.tint)
                .frame(width: 22, height: 22)
                .background(entry.tint.opacity(0.12), in: Circle())

            VStack(alignment: .leading, spacing: 3) {
                Text(entry.headline)
                    .font(.subheadline)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 4) {
                    Text(entry.date, format: .relative(presentation: .numeric))
                    if let summary = entry.notificationSummary {
                        Text("·")
                        Label(summary, systemImage: "bell.badge")
                            .labelStyle(.titleAndIcon)
                    }
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}
