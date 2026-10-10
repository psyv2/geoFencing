# GeoGroup

An iOS app for tracking a group against a shared geofence. A group creates a circular
zone; when any member leaves it, the other members are notified.

Built with Kotlin Multiplatform for the shared logic and SwiftUI for the app.

## Structure

```
shared/     Kotlin Multiplatform module (iOS + JVM targets)
iosApp/     SwiftUI app
```

The shared module owns the decisions: whether a position counts as outside the fence,
who should be notified, and the notification text. The iOS layer supplies location
fixes and delivers notifications.

`AlertPresenter` is a Kotlin interface implemented by a Swift class, so Kotlin passes
down a finished alert and Swift only hands it to `UNUserNotificationCenter`. That keeps
the notification rules unit-testable.

Key types:

- `GeofenceEvaluator` decides inside/outside. Pure function, no clock or storage.
- `GroupStore` holds groups and membership, and produces the alerts on a transition.
- `GeoGroupSdk` is the only type the Swift layer talks to.
- `LocationService` wraps CoreLocation.

## Detection

Plain `distance > radius` produces constant false alarms, since GPS drifts by tens of
metres while a phone is stationary. Three rules handle that:

- 25 m hysteresis. A member has to clear the edge plus the dead band before the state
  changes, so someone standing on the boundary produces no notifications.
- Accuracy gating. Fixes worse than 100 m are dropped, and an exit also requires the
  whole accuracy circle to be outside the fence.
- The first fix never alerts. New members start in an UNKNOWN state and the first
  usable fix adopts inside or outside silently.

Editing a zone resets every member to UNKNOWN, since a verdict about the old boundary
says nothing about the new one.

Invite codes use Crockford base32 (no I, L, O or U) so a code read aloud cannot be
misheard as a different valid code.

## Background

`CLCircularRegion` monitoring is the only way to wake a closed app. The region callback
is treated as a wake-up only: it requests a fresh fix, which goes through the same
evaluator. Background and foreground breaches are judged by the same rules.

Requires "Always" location permission. iOS monitors at most 20 regions per app; beyond
that, groups only update while the app is open, and the UI says so.

## Building

Requires Xcode 16+ and a JDK 17 or newer. Install
[XcodeGen](https://github.com/yonaskolb/XcodeGen), then:

```sh
cd iosApp
xcodegen generate
open GeoGroup.xcodeproj
```

Build and run. An Xcode pre-build phase runs Gradle to build and embed
`Shared.framework` for the current SDK and architecture.

If `xcode-select` points at CommandLineTools rather than Xcode, command-line
Kotlin/Native builds cannot find the iOS SDK:

```sh
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

The project uses Gradle 8.14 via the wrapper, since the Kotlin 2.2 Multiplatform plugin
does not support Gradle 9.

## Tests

```sh
./gradlew :shared:jvmTest                 # fast, no native compile
./gradlew :shared:iosSimulatorArm64Test   # same tests on Kotlin/Native
```

72 tests covering the geometry, the evaluator's edge cases, invite codes, the alert
fan-out, and persistence.

## Limitations

- No backend. Groups live in `NSUserDefaults` on one device, so an invite code only
  resolves against groups that device knows about. Other members are simulated: their
  positions are generated, but those positions go through `GroupStore.recordLocation`,
  the same entry point real fixes use.
- Simulated movement is faster than walking pace so crossings are observable by hand.
- Tapping a notification opens the app but does not deep-link to the group.
- Only `iosArm64` and `iosSimulatorArm64` are built. Add `iosX64` for Intel simulators.
