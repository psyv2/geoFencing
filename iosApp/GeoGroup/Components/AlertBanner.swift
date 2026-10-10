import Shared
import SwiftUI

/// In-app fallback shown when notification permission has been declined.
struct AlertBanner: View {

    let alert: BreachAlert
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: iconName)
                .font(.title3)
                .foregroundStyle(alert.tint)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(alert.title)
                    .font(.subheadline.weight(.semibold))
                Text(alert.body)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(alert.tint.opacity(0.35), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
    }

    private var iconName: String {
        switch alert.severity {
        case AlertSeverity.breach: return "exclamationmark.triangle.fill"
        case AlertSeverity.info: return "checkmark.circle.fill"
        default: return "paperplane.circle.fill"
        }
    }
}
