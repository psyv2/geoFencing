package com.geogroup.shared

import com.geogroup.shared.geo.GeoMath
import com.geogroup.shared.geo.GeofenceEvaluator
import com.geogroup.shared.model.Coordinate
import com.geogroup.shared.model.LocationSample
import com.geogroup.shared.platform.FixedTimeProvider
import com.geogroup.shared.platform.InMemoryKeyValueStore
import com.geogroup.shared.platform.KeyValueStore
import com.geogroup.shared.store.GroupStore
import kotlin.random.Random

internal const val TEST_EPOCH: Long = 1_700_000_000_000L

internal val DELHI = Coordinate(28.6129, 77.2295)

internal fun testStore(
    keyValueStore: KeyValueStore = InMemoryKeyValueStore(),
    time: FixedTimeProvider = FixedTimeProvider(TEST_EPOCH),
    evaluator: GeofenceEvaluator = GeofenceEvaluator(),
    // Fixed seed: demo-member names and ids are random, and a flaky fixture is worse
    // than a boring one.
    random: Random = Random(1234),
): GroupStore = GroupStore(
    keyValueStore = keyValueStore,
    time = time,
    evaluator = evaluator,
    random = random,
)

/** A fix [distanceFromCenter] metres from [center], on a fixed bearing. */
internal fun sampleAt(
    center: Coordinate,
    distanceFromCenter: Double,
    accuracy: Double = 5.0,
    bearing: Double = 30.0,
    timestamp: Long = TEST_EPOCH,
) = LocationSample(
    coordinate = GeoMath.offset(center, distanceFromCenter, bearing),
    horizontalAccuracyMeters = accuracy,
    timestampEpochMillis = timestamp,
)
