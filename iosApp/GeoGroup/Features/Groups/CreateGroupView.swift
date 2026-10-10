import CoreLocation
import MapKit
import Shared
import SwiftUI

/// A group and its zone are created together; one is useless without the other.
struct CreateGroupView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var fenceLabel = ""
    @State private var radius: Double = 300
    @State private var center: CLLocationCoordinate2D?
    @State private var camera: MapCameraPosition = .automatic
    @State private var addDemoMembers = true

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canSave: Bool { !trimmedName.isEmpty && center != nil }

    var body: some View {
        NavigationStack {
            Form {
                Section("Group") {
                    TextField("Group name", text: $name)
                        .textInputAutocapitalization(.words)
                    TextField("Zone name (e.g. Base Camp)", text: $fenceLabel)
                        .textInputAutocapitalization(.words)
                }

                Section {
                    mapPicker
                        .frame(height: 260)
                        .listRowInsets(EdgeInsets())
                } header: {
                    Text("Zone")
                } footer: {
                    Text(center == nil
                         ? "Waiting for your location. You can also tap the map to place the zone."
                         : "Tap the map to move the centre.")
                }

                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Radius")
                            Spacer()
                            Text("\(Int(radius)) m").foregroundStyle(.secondary).monospacedDigit()
                        }
                        Slider(value: $radius, in: 50...5000, step: 25)
                    }
                } footer: {
                    Text("A member must get about 25 m past this edge before it counts as leaving.")
                }

                Section {
                    Toggle("Add 3 demo members", isOn: $addDemoMembers)
                } footer: {
                    Text("No server in this build, so demo members stand in for real ones. They can be moved in and out of the zone.")
                }
            }
            .navigationTitle("New group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create", action: create).disabled(!canSave)
                }
            }
            .onAppear(perform: seedCenterFromCurrentLocation)
            .onChange(of: model.currentCoordinate?.latitude) { _, _ in
                seedCenterFromCurrentLocation()
            }
        }
    }

    private var mapPicker: some View {
        MapReader { proxy in
            Map(position: $camera) {
                if let center {
                    MapCircle(center: center, radius: radius)
                        .foregroundStyle(.blue.opacity(0.15))
                        .stroke(.blue.opacity(0.8), lineWidth: 2)
                    Annotation(fenceLabel.isEmpty ? "Zone" : fenceLabel, coordinate: center) {
                        Image(systemName: "mappin.circle.fill").foregroundStyle(.blue)
                    }
                }
                UserAnnotation()
            }
            .mapStyle(.standard(elevation: .flat))
            .onTapGesture { point in
                guard let coordinate = proxy.convert(point, from: .local) else { return }
                center = coordinate
            }
        }
    }

    /// Seeds the fence on the first fix, never overwriting a centre the user tapped.
    private func seedCenterFromCurrentLocation() {
        guard center == nil, let coordinate = model.currentCoordinate else { return }
        center = coordinate
        camera = .region(
            MKCoordinateRegion(
                center: coordinate,
                latitudinalMeters: radius * 4,
                longitudinalMeters: radius * 4
            )
        )
    }

    private func create() {
        guard let center else { return }
        model.createGroup(
            name: trimmedName,
            fenceLabel: fenceLabel.trimmingCharacters(in: .whitespacesAndNewlines),
            center: center,
            radiusMeters: radius,
            addDemoMembers: addDemoMembers
        )
        dismiss()
    }
}
