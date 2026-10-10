import CoreLocation
import MapKit
import Shared
import SwiftUI

struct EditFenceView: View {

    let groupId: String

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var label = ""
    @State private var radius: Double = 300
    @State private var center = CLLocationCoordinate2D(latitude: 0, longitude: 0)
    @State private var camera: MapCameraPosition = .automatic
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    MapReader { proxy in
                        Map(position: $camera) {
                            MapCircle(center: center, radius: radius)
                                .foregroundStyle(.blue.opacity(0.15))
                                .stroke(.blue.opacity(0.8), lineWidth: 2)
                            Annotation(label.isEmpty ? "Zone" : label, coordinate: center) {
                                Image(systemName: "mappin.circle.fill").foregroundStyle(.blue)
                            }
                            UserAnnotation()
                        }
                        .mapStyle(.standard(elevation: .flat))
                        .onTapGesture { point in
                            guard let coordinate = proxy.convert(point, from: .local) else { return }
                            center = coordinate
                        }
                    }
                    .frame(height: 260)
                    .listRowInsets(EdgeInsets())
                } footer: {
                    Text("Tap to move the centre.")
                }

                Section("Zone") {
                    TextField("Zone name", text: $label)
                        .textInputAutocapitalization(.words)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Radius")
                            Spacer()
                            Text("\(Int(radius)) m").foregroundStyle(.secondary).monospacedDigit()
                        }
                        Slider(value: $radius, in: 50...5000, step: 25)
                    }
                }

                Section {
                    Button("Save zone", action: save)
                } footer: {
                    Text("Everyone's status resets when the zone changes, so nobody is alerted for a move they did not make.")
                }
            }
            .navigationTitle("Edit zone")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard !loaded, let group = model.group(id: groupId) else { return }
        loaded = true
        label = group.geofence.label
        radius = group.geofence.radiusMeters
        center = group.geofence.center.clCoordinate
        camera = .region(
            MKCoordinateRegion(
                center: center,
                latitudinalMeters: radius * 4,
                longitudinalMeters: radius * 4
            )
        )
    }

    private func save() {
        model.updateGeofence(
            groupId: groupId,
            fenceLabel: label.trimmingCharacters(in: .whitespacesAndNewlines),
            center: center,
            radiusMeters: radius
        )
        dismiss()
    }
}
