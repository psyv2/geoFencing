package com.geogroup.shared.platform

import platform.Foundation.NSUserDefaults

private class UserDefaultsKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStore {

    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun delete(key: String) {
        defaults.removeObjectForKey(key)
    }
}

actual fun defaultKeyValueStore(): KeyValueStore = UserDefaultsKeyValueStore()
