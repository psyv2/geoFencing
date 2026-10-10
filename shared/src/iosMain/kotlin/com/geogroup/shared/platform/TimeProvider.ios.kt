package com.geogroup.shared.platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun systemTimeProvider(): TimeProvider =
    TimeProvider { (NSDate().timeIntervalSince1970 * 1000.0).toLong() }
