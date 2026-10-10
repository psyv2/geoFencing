package com.geogroup.shared.platform

import java.util.prefs.Preferences

private class PreferencesKeyValueStore(
    private val preferences: Preferences = Preferences.userRoot().node("com.geogroup.shared"),
) : KeyValueStore {

    override fun read(key: String): String? = preferences.get(key, null)

    override fun write(key: String, value: String) {
        preferences.put(key, value)
        preferences.flush()
    }

    override fun delete(key: String) {
        preferences.remove(key)
        preferences.flush()
    }
}

actual fun defaultKeyValueStore(): KeyValueStore = PreferencesKeyValueStore()
