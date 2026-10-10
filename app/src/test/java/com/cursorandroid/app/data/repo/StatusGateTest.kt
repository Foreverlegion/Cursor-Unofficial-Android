package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class StatusGateTest {
    private var clock = 1_000_000L
    private val gate = StatusGate(now = { clock })

    private fun limited(retryAfterMs: Long? = null) =
        ApiException(429, "You have exceeded the rate limit of 6000 requests per hour for the get_agent_status endpoint", retryAfterMs = retryAfterMs)

    private fun hit(retryAfterMs: Long? = null) = runBlocking {
        try {
            gate.call("agent") { throw limited(retryAfterMs) }
            fail("expected 429")
        } catch (e: ApiException) {
            assertTrue(e.isRateLimited)
        }
    }

    @Test
    fun backoffDoublesFromFiveToSixtySeconds() {
        val steps = (1..6).map { StatusGate.backoffMs(it) }
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L), steps)
    }

    @Test
    fun aRateLimitShutsTheGateAndLaterCallsFailLocallyWithoutTouchingTheNetwork() = runBlocking {
        hit()
        assertEquals(5_000L, gate.remainingMs())
        val network = AtomicInteger()
        try {
            gate.call("agent") { network.incrementAndGet() }
            fail("expected a local pause")
        } catch (e: ApiException) {
            assertTrue(e.isRateLimited)
            assertEquals(5_000L, e.retryAfterMs)
        }
        assertEquals(0, network.get())
        assertEquals(1, gate.meter.lastMinute())
    }

    @Test
    fun theGateReopensAfterThePauseAndAnotherLimitDoublesIt() = runBlocking {
        hit()
        clock += 5_001
        assertFalse(gate.isPaused)
        hit()
        assertEquals(10_000L, gate.remainingMs())
        clock += 10_001
        hit()
        assertEquals(20_000L, gate.remainingMs())
    }

    @Test
    fun aSuccessResetsTheBackoff() = runBlocking {
        hit()
        clock += 6_000
        assertEquals("ok", gate.call("agent") { "ok" })
        hit()
        assertEquals(5_000L, gate.remainingMs())
    }

    @Test
    fun retryAfterWinsWhenItIsLongerThanTheBackoff() {
        hit(retryAfterMs = 42_000)
        assertEquals(42_000L, gate.remainingMs())
        assertEquals(42_000L, gate.pausedUntil.value - clock)
    }

    @Test
    fun retryAfterIsCappedAtTheMaximumPause() {
        hit(retryAfterMs = 10 * 60_000)
        assertEquals(StatusGate.MAX_PAUSE_MS, gate.remainingMs())
    }

    @Test
    fun requestsThatWereAlreadyInFlightDoNotCountAsNewStrikes() {
        hit()
        hit()
        hit()
        assertEquals(5_000L, gate.remainingMs())
    }

    @Test
    fun otherErrorsDoNotCloseTheGate() = runBlocking {
        try {
            gate.call("agent") { throw ApiException(500, "boom") }
        } catch (_: ApiException) {
        }
        assertFalse(gate.isPaused)
    }

    @Test
    fun meterCountsCallsPerMinuteAndSplitsByKind() = runBlocking {
        repeat(3) { gate.call("agent") { } }
        gate.call("run") { }
        assertEquals(4, gate.meter.lastMinute())
        assertEquals(mapOf("agent" to 3, "run" to 1), gate.meter.lastMinuteByKind())
        clock += 61_000
        assertEquals(0, gate.meter.lastMinute())
    }

    @Test
    fun meterLogsAMinuteSummaryWhenTheWindowRollsOver() {
        val lines = ArrayList<String>()
        val meter = StatusCallMeter(now = { clock }, sink = { lines += it })
        repeat(5) { meter.record("agent") }
        assertTrue(lines.isEmpty())
        clock += 61_000
        meter.record("run")
        assertEquals(1, lines.size)
        assertTrue(lines.single(), lines.single().contains("5") && lines.single().contains("agent=5"))
    }

    @Test
    fun retryAfterHeaderAcceptsSecondsAndHttpDates() {
        assertEquals(30_000L, ApiException.parseRetryAfter("30"))
        assertNull(ApiException.parseRetryAfter(""))
        assertNull(ApiException.parseRetryAfter("soon"))
        val now = 1_700_000_000_000L
        val date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
            .format(java.time.Instant.ofEpochMilli(now + 20_000).atZone(java.time.ZoneOffset.UTC))
        assertEquals(20_000L, ApiException.parseRetryAfter(date, now))
        assertNotNull(ApiException.fromBody(429, "{}", "7").retryAfterMs)
        assertEquals(7_000L, ApiException.fromBody(429, "", "7").retryAfterMs)
    }

    @Test
    fun the429MessageFromTheServerIsRecognisedEvenWithoutAStatusCode() {
        assertTrue(ApiException(0, "You have exceeded the rate limit of 6000 requests per hour").isRateLimited)
        assertTrue(ApiException(200, "x", errorCode = "rate_limit_exceeded").isRateLimited)
        assertFalse(ApiException(500, "boom").isRateLimited)
    }

    @Test
    fun simultaneousCallersForTheSameKeyShareOneRequest() = runBlocking {
        val cache = StatusCache(now = { clock }, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val loads = AtomicInteger()
        val all = (1..8).map {
            async(Dispatchers.Default) {
                cache.get("agent:a", 1_000) {
                    loads.incrementAndGet()
                    delay(100)
                    "value"
                }
            }
        }.awaitAll()
        assertEquals(List(8) { "value" }, all)
        assertEquals(1, loads.get())
    }

    @Test
    fun aFreshAnswerIsReusedUntilItsTtlRunsOut() = runBlocking {
        val cache = StatusCache(now = { clock }, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val loads = AtomicInteger()
        suspend fun read() = cache.get("agent:a", 1_000) { loads.incrementAndGet() }
        assertEquals(1, read())
        assertEquals(1, read())
        clock += 1_001
        assertEquals(2, read())
        cache.invalidate("agent:a")
        assertEquals(3, read())
    }

    @Test
    fun failuresAreNotCached() = runBlocking {
        val cache = StatusCache(now = { clock }, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val loads = AtomicInteger()
        repeat(2) {
            try {
                cache.get<String>("run:a:r", 1_000) {
                    loads.incrementAndGet()
                    throw limited()
                }
                fail("expected an error")
            } catch (_: ApiException) {
            }
        }
        assertEquals(2, loads.get())
    }
}
