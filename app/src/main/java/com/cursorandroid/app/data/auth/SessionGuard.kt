package com.cursorandroid.app.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers which Cursor API key the server refused. While that same key is still the saved one, nothing
 * calls the API: requests fail locally and pollers stop. Saving a different key (signing in again) lifts it.
 */
class SessionGuard(private val currentKey: () -> String?) {
    private val failed = MutableStateFlow<String?>(null)

    /** The key that was rejected, or null. Compare with the saved key to know whether the session is expired. */
    val rejectedKey: StateFlow<String?> = failed.asStateFlow()

    fun expired(): Boolean = expiredFor(currentKey())

    fun expiredFor(key: String?): Boolean {
        val bad = failed.value ?: return false
        return !key.isNullOrBlank() && bad == key.trim()
    }

    fun markExpired(usedKey: String?) {
        val key = usedKey?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (key == currentKey()?.trim()) failed.value = key
    }

    fun reset() {
        failed.value = null
    }
}
