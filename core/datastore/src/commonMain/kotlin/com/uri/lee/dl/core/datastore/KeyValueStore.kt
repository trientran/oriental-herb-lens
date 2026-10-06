package com.uri.lee.dl.core.datastore

import kotlinx.coroutines.flow.Flow

/**
 * A small typed preference store: DataStore files on Android and iOS, `localStorage` in the
 * browser (DataStore has no stable JS target). Inject one with `get(named(PreferenceStore.SETTINGS))`.
 */
interface KeyValueStore {
    val data: Flow<StoreValues>

    /** Applies [transform] atomically and persists the result. */
    suspend fun edit(transform: (MutableStoreValues) -> Unit)
}

interface StoreValues {
    operator fun <T : Any> get(key: StoreKey<T>): T?
}

interface MutableStoreValues : StoreValues {
    operator fun <T : Any> set(key: StoreKey<T>, value: T)
    fun remove(key: StoreKey<*>)
}

/** A typed key. Names are persisted: renaming one loses what users saved under it. */
class StoreKey<T : Any> internal constructor(val name: String, internal val type: Type) {
    internal enum class Type { BOOLEAN, FLOAT, STRING, STRING_SET }

    override fun equals(other: Any?) = other is StoreKey<*> && other.name == name && other.type == type
    override fun hashCode() = name.hashCode()
    override fun toString() = name
}

fun booleanKey(name: String) = StoreKey<Boolean>(name, StoreKey.Type.BOOLEAN)
fun floatKey(name: String) = StoreKey<Float>(name, StoreKey.Type.FLOAT)
fun stringKey(name: String) = StoreKey<String>(name, StoreKey.Type.STRING)
fun stringSetKey(name: String) = StoreKey<Set<String>>(name, StoreKey.Type.STRING_SET)
