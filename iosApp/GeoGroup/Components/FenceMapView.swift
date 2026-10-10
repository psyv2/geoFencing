import MapKit
import Shared
import SwiftUI

/// The fence and everyone in it. Members outside are drawn in red with their overshoot.
struct FenceMapView: View {

    let geofence: Geofence
    let members: [Member]
    var interactive: Bool = true

    @State private var camera: MapCameraPosition = .automatic

    var body: some View {
        Map(position: $camera, interactionModes: interactive ? .all : []) {
            MapCircle(center: geofence.center.clCoordinate, radius: geofence.radiusMeters)
                .foregroundStyle(.blue.opacity(0.12))
                .stroke(.blue.opacity(0.7), lineWidth: 2)

            Annotation(geofence.label, coordinate: geofence.center.clCoordinate) {
                Image(systemName: "mappin.circle.fill")
                    .font(.caption)
                    .foregroundStyle(.blue)
            }

            ForEach(members) { member in
                if let location = member.lastKnownLocation {
                    Annotation(member.displayName, coordinate: location.clCoordinate) {
                        MemberPin(member: member)
                    }
                }
            }
        }
        .mapStyle(.standard(elevation: .flat))
        .onAppear { frameFence() }
        .onChange(of: geofence.center.latitude) { _, _ in frameFence() }
        .onChange(of: geofence.radiusMeters) { _, _ in frameFence() }
    }

    private func frameFence() {
        // 3x the radius, so members who just stepped outside are still visible.
        let span = max(geofence.radiusMeters * 3, 400)
        camera = .region(
            MKCoordinateRegion(
                center: geofence.center.clCoordinate,
                latitudinalMeters: span,
                longitudinalMeters: span
            )
        )
    }
}

struct MemberPin: View {

    let member: Member

    var body: some View {
        VStack(spacing: 2) {
            Text(member.initials)
                .font(.caption2.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(member.isOutside ? Color.red : Color.green, in: Circle())
                .overlay(
                    Circle().stroke(member.isSelf ? Color.primary : .clear, lineWidth: 2)
                )
            if member.isOutside, let distance = member.signedDistanceFromEdge {
                Text(Member.format(distance))
                    .font(.system(size: 9, weight: .semibold))
                    .padding(.horizontal, 4)
                    .padding(.vertical, 1)
                    .background(.red, in: Capsule())
                    .foregroundStyle(.white)
            }
        }
    }
}
