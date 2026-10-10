package com.geogroup.shared.platform

actual fun systemTimeProvider(): TimeProvider = TimeProvider { System.currentTimeMillis() }
