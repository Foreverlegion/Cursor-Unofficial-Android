package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Counts calls to the agent and run status endpoints so a debug build can show how hard it is polling. */
class StatusCallMeter(
    private val now: () -> Long = System::currentTimeMillis,
    private val sink: ((String) -> Unit)? = null,
) {
    private val lock = Any()
    private val stamps = ArrayDeque<Pair<Long, String>>()
    private var windowStart = Long.MIN_VALUE
    private var windowCount = 0
    private var windowKinds = HashMap<String, Int>()

    fun record(kind: String) {
        val at = now()
        var report: String? = null
        synchronized(lock) {
            stamps.addLast(at to kind)
            while (stamps.isNotEmpty() && at - stamps.first().first >= WINDOW_MS) stamps.removeFirst()
            if (windowStart == Long.MIN_VALUE) windowStart = at
            if (at - windowStart >= WINDOW_MS) {
                if (windowCount > 0) report = describe(windowCount, windowKinds)
                windowStart = at
                windowCount = 0
                windowKinds = HashMap()
            }
            windowCount++
            windowKinds.merge(kind, 1, Int::plus)
        }
        report?.let { sink?.invoke(it) }
    }

    fun lastMinute(): Int = synchronized(lock) {
        val at = now()
        stamps.count { at - it.first < WINDOW_MS }
    }

    fun lastMinuteByKind(): Map<String, Int> = synchronized(lock) {
        val at = now()
        stamps.filter { at - it.first < WINDOW_MS }.groupingBy { it.second }.eachCount()
    }

    fun note(message: String) {
        sink?.invoke(message)
    }

    private fun describe(count: Int, kinds: Map<String, Int>): String =
        "status calls in the last minute: $count (" + kinds.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" } + ")"

    companion object {
        const val WINDOW_MS = 60_000L
    }
}

/**
 * One switch in front of every status call. After a 429 it stays shut for an exponentially growing time
 * (5s, 10s, ... 60s) or the server's Retry-After, whichever is longer, and calls made meanwhile fail
 * locally without touching the network. A success resets the backoff.
 */
class StatusGate(
    private val now: () -> Long = System::currentTimeMillis,
    val meter: StatusCallMeter = StatusCallMeter(now),
) {
    private val lock = Any()
    private var strikes = 0
    private val until = MutableStateFlow(0L)

    /** Epoch millis until which status calls are held back, or 0 when they are not. */
    val pausedUntil: StateFlow<Long> = until.asStateFlow()

    fun remainingMs(): Long = (until.value - now()).coerceAtLeast(0)

    val isPaused: Boolean get() = remainingMs() > 0

    fun onRateLimited(retryAfterMs: Long?): Long {
        val pause: Long
        synchronized(lock) {
            val live = until.value > now()
            // Calls that were already in flight when the first 429 landed must not count as new strikes.
            if (!live) strikes++
            val backoff = backoffMs(strikes)
            pause = maxOf(backoff, retryAfterMs ?: 0L).coerceAtMost(MAX_PAUSE_MS)
            val target = now() + pause
            if (target > until.value) until.value = target
        }
        meter.note("rate limited: status calls paused for ${pause / 1000}s (strike $strikes)")
        return pause
    }

    fun onSuccess() {
        synchronized(lock) {
            if (until.value > now()) return
            strikes = 0
            if (until.value != 0L) until.value = 0L
        }
    }

    suspend fun <T> call(kind: String, block: suspend () -> T): T {
        val wait = remainingMs()
        if (wait > 0) {
            throw ApiException(429, "Refresh paused", errorCode = LOCAL_PAUSE, retryAfterMs = wait)
        }
        meter.record(kind)
        try {
            val value = block()
            onSuccess()
            return value
        } catch (e: ApiException) {
            if (e.isRateLimited) onRateLimited(e.retryAfterMs)
            throw e
        }
    }

    companion object {
        const val BASE_MS = 5_000L
        const val MAX_PAUSE_MS = 60_000L
        const val LOCAL_PAUSE = "status_paused"

        fun backoffMs(strikes: Int): Long {
            if (strikes <= 0) return 0L
            return (BASE_MS shl (strikes - 1).coerceAtMost(8)).coerceAtMost(MAX_PAUSE_MS)
        }
    }
}

/**
 * Callers that ask for the same agent or run at the same moment share one request, and a fresh answer is
 * reused for a short while, so a thread, the inbox and a worker never each pay for their own copy.
 */
class StatusCache(
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private class Entry(val at: Long, val value: Any?)

    private val lock = Any()
    private val fresh = HashMap<String, Entry>()
    private val flights = HashMap<String, Deferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(key: String, ttlMs: Long, load: suspend () -> T): T {
        val flight: Deferred<Any?>
        synchronized(lock) {
            val hit = fresh[key]
            if (hit != null && now() - hit.at < ttlMs) return hit.value as T
            flight = flights.getOrPut(key) {
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        val value = load()
                        synchronized(lock) {
                            val at = now()
                            fresh.entries.removeAll { at - it.value.at > EVICT_MS }
                            fresh[key] = Entry(at, value)
                        }
                        value
                    } finally {
                        synchronized(lock) { flights.remove(key) }
                    }
                }
            }
        }
        flight.start()
        return flight.await() as T
    }

    fun invalidate(prefix: String) {
        synchronized(lock) { fresh.keys.removeAll { it.startsWith(prefix) } }
    }

    companion object {
        const val EVICT_MS = 60_000L
    }
}
