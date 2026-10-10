package com.uri.lee.dl.domain.upload

/**
 * Uploads of one kind (photos, a shared model) saved in the app's files before they start, so
 * they finish even if the app is closed or loses its connection: whatever is left runs at the
 * next chance.
 */
interface UploadQueue {
    /** Tries every saved job once; one that fails stays saved for the next try, up to a limit. */
    suspend fun runPending()

    /** Whether any job is still saved (to try again later). */
    suspend fun hasPending(): Boolean
}

/**
 * Has the saved uploads run soon. Android uses WorkManager, which carries on after the app is
 * swiped away and waits for a connection; elsewhere they run in the app's scope (with background
 * time on iOS) and, if the app is closed first, at the next launch.
 */
fun interface UploadScheduler {
    fun schedule()
}

/** A job's settings: one `key=value` per line; repeated keys (each finished step) are kept in order. */
internal class JobFile(text: String) {
    private val lines = text.lines().filter { '=' in it }.map { it.substringBefore('=') to it.substringAfter('=') }
    operator fun get(key: String): String? = lines.firstOrNull { it.first == key }?.second
    fun all(key: String): List<String> = lines.filter { it.first == key }.map { it.second }

    companion object {
        fun encode(values: List<Pair<String, Any?>>) =
            values.filter { it.second != null }.joinToString("") { (k, v) -> "$k=${v.toString().replace('\n', ' ')}\n" }
    }
}
