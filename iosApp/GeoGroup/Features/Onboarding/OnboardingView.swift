import Shared
import SwiftUI

struct OnboardingView: View {

    @Environment(AppModel.self) private var model
    @State private var name: String = ""

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 16) {
                Image(systemName: "dot.radiowaves.left.and.right")
                    .font(.system(size: 52))
                    .foregroundStyle(.tint)

                Text("GeoGroup")
                    .font(.largeTitle.weight(.bold))

                Text("Draw a zone around your group. If anyone steps outside it, everyone else finds out.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            Spacer()

            VStack(alignment: .leading, spacing: 8) {
                Text("What should the group call you?")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.secondary)

                TextField("Your name", text: $name)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .onSubmit(continueIfPossible)

                Text("No account needed. The next screen asks for background location, which is what lets your group be told when you leave a zone.")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 24)

            Button(action: continueIfPossible) {
                Text("Continue")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(trimmedName.isEmpty)
            .padding(.horizontal, 24)
            .padding(.top, 20)
            .padding(.bottom, 32)
        }
    }

    private func continueIfPossible() {
        guard !trimmedName.isEmpty else { return }
        model.completeOnboarding(name: trimmedName)
    }
}
