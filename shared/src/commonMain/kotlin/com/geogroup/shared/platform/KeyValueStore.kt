package com.geogroup.shared.platform

/**
 * The whole persistence surface the shared module needs. Backed by `NSUserDefaults` on
 * iOS and a map in tests.
 */
interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun delete(key: String)
}

class InMemoryKeyValueStore(
    initial: Map<String, String> = emptyMap(),
) : KeyValueStore {

    private val values: MutableMap<String, String> = initial.toMutableMap()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        values[key] = value
    }

    override fun delete(key: String) {
        values.remove(key)
    }
}

expect fun defaultKeyValueStore(): KeyValueStore
