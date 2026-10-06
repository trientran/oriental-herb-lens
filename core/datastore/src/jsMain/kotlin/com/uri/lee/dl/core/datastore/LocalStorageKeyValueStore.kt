package com.uri.lee.dl.core.datastore

import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.scope.Scope

/**
 * A [KeyValueStore] kept as one JSON object in `localStorage`, under `herblens.<file name>`.
 * Without storage (blocked site data, some private windows) values last until the page closes.
 */
internal class LocalStorageKeyValueStore(private val storageKey: String) : KeyValueStore {
    private val values = MutableStateFlow(load())
    private val mutex = Mutex()

    override val data: Flow<StoreValues> = values

    override suspend fun edit(transform: (MutableStoreValues) -> Unit) = mutex.withLock {
        val edited = Values(values.value.map.toMutableMap())
        transform(edited)
        save(edited.map)
        values.value = edited
    }

    private class Values(val map: MutableMap<String, Any>) : MutableStoreValues {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> get(key: StoreKey<T>): T? = map[key.name]?.let { value ->
            when (key.type) {
                StoreKey.Type.BOOLEAN -> value as? Boolean
                StoreKey.Type.FLOAT -> (value as? Number)?.toFloat()
                StoreKey.Type.STRING -> value as? String
                StoreKey.Type.STRING_SET -> (value as? Set<*>)?.filterIsInstance<String>()?.toSet()
            } as T?
        }

        override fun <T : Any> set(key: StoreKey<T>, value: T) { map[key.name] = value }
        override fun remove(key: StoreKey<*>) { map.remove(key.name) }
    }

    private fun load(): Values {
        val json = runCatching { window.localStorage.getItem(storageKey) }.getOrNull() ?: return Values(mutableMapOf())
        val parsed: dynamic = runCatching { JSON.parse<dynamic>(json) }.getOrNull() ?: return Values(mutableMapOf())
        val map = mutableMapOf<String, Any>()
        for (name in js("Object").keys(parsed).unsafeCast<Array<String>>()) {
            val value: dynamic = parsed[name]
            map[name] = when {
                // Sets are stored as arrays
                js("Array").isArray(value) as Boolean -> value.unsafeCast<Array<String>>().toSet()
                else -> value.unsafeCast<Any>()
            }
        }
        return Values(map)
    }

    private fun save(map: Map<String, Any>) {
        val obj: dynamic = js("({})")
        map.forEach { (name, value) -> obj[name] = if (value is Set<*>) value.toTypedArray() else value }
        runCatching { window.localStorage.setItem(storageKey, JSON.stringify(obj)) }
    }
}

internal actual fun Scope.createKeyValueStore(store: PreferenceStore): KeyValueStore =
    LocalStorageKeyValueStore("herblens.${store.fileName}")
