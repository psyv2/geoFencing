import Shared
import SwiftUI

struct MemberRow: View {

    let member: Member
    var onSimulateExit: (() -> Void)?
    var onSimulateReturn: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            Text(member.initials)
                .font(.footnote.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(member.statusColor.gradient, in: Circle())

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(member.isSelf ? "\(member.displayName) (you)" : member.displayName)
                        .font(.subheadline.weight(.medium))
                    if member.isOutside {
                        Text("OUTSIDE")
                            .font(.system(size: 9, weight: .heavy))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(.red, in: Capsule())
                            .foregroundStyle(.white)
                    }
                }
                HStack(spacing: 4) {
                    Text(member.statusText)
                    if let seen = member.lastSeenDate {
                        Text("·")
                        Text(seen, format: .relative(presentation: .numeric))
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Spacer()

            if onSimulateExit != nil || onSimulateReturn != nil {
                Menu {
                    if let onSimulateExit {
                        Button("Move outside the zone", systemImage: "arrow.up.right", action: onSimulateExit)
                    }
                    if let onSimulateReturn {
                        Button("Move back inside", systemImage: "arrow.down.left", action: onSimulateReturn)
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 2)
    }
}
