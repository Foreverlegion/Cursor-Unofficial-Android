package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallPulseTest {
    private val id = "a".repeat(64)
    private val other = "b".repeat(64)

    @Test
    fun ownerIsCursorEmailOnly() {
        assertTrue(InstallPulse.isOwner("Foreverlegion@gmail.com"))
        assertTrue(InstallPulse.isOwner(" foreverlegion@gmail.com "))
        assertFalse(InstallPulse.isOwner("someone@example.com"))
        assertFalse(InstallPulse.isOwner(null))
        assertFalse(InstallPulse.isOwner(""))
    }

    @Test
    fun hashIsStableHex() {
        val hash = InstallPulse.hashId("phone-one")
        assertEquals("5b2053752d067ecf9d2d58792be7255b405f1e628bdec6d24260fe9fff547961", hash)
        assertEquals(hash, InstallPulse.hashId("phone-one"))
        assertTrue(hash != InstallPulse.hashId("phone-two"))
    }

    @Test
    fun pingDueAfterGap() {
        assertTrue(InstallPulse.pingDue(0L, 1000L))
        assertFalse(InstallPulse.pingDue(1_000L, 1_000L + InstallPulse.PING_EVERY_MS - 1))
        assertTrue(InstallPulse.pingDue(1_000L, 1_000L + InstallPulse.PING_EVERY_MS))
    }

    @Test
    fun parseIgnoresJunkIdsAndReadsLegacyTimestamps() {
        val body = """
            <!-- install-ledger -->
            ```json
            {"windowDays":14,"ids":{"$id":100,"nope":200}}
            ```
        """.trimIndent()
        val ledger = InstallPulse.parseLedger(body)
        assertEquals(14L, ledger.windowDays)
        assertEquals(InstallPulse.Sighting(100, 100), ledger.ids[id])
        assertEquals(1, ledger.ids.size)
    }

    @Test
    fun samePhonePingDoesNotRaiseTotal() {
        val now = 1_000_000L
        val first = InstallPulse.apply(InstallPulse.Ledger(), id, now, leave = false)
        val again = InstallPulse.apply(first, id, now + 60, leave = false)
        assertEquals(1, InstallPulse.counts(first, now).total)
        assertEquals(1, InstallPulse.counts(again, now + 60).total)
        assertEquals(1, InstallPulse.counts(again, now + 60).current)
        assertEquals(now, again.ids[id]?.first)
        assertEquals(now + 60, again.ids[id]?.last)
    }

    @Test
    fun newPhoneRaisesTotal() {
        val now = 1_000_000L
        val one = InstallPulse.apply(InstallPulse.Ledger(), id, now, leave = false)
        val two = InstallPulse.apply(one, other, now, leave = false)
        assertEquals(2, InstallPulse.counts(two, now).total)
        assertEquals(2, InstallPulse.counts(two, now).current)
    }

    @Test
    fun leaveDropsCurrentKeepsTotal() {
        val now = 1_000_000L
        val afterPing = InstallPulse.apply(InstallPulse.Ledger(), id, now, leave = false)
        val afterLeave = InstallPulse.apply(afterPing, id, now + 10, leave = true)
        val counted = InstallPulse.counts(afterLeave, now + 10)
        assertEquals(0, counted.current)
        assertEquals(1, counted.total)
    }

    @Test
    fun staleIdsDropFromCurrentOnly() {
        val now = 2_000_000L
        val stale = now - (InstallPulse.WINDOW_DAYS * 86_400L) - 1
        val ledger = InstallPulse.Ledger(
            ids = mapOf(
                id to InstallPulse.Sighting(stale, stale),
                other to InstallPulse.Sighting(now, now),
            ),
        )
        val counted = InstallPulse.counts(ledger, now)
        assertEquals(1, counted.current)
        assertEquals(2, counted.total)
    }

    @Test
    fun renderRoundTrip() {
        val ledger = InstallPulse.Ledger(ids = mapOf(id to InstallPulse.Sighting(40, 42)))
        val again = InstallPulse.parseLedger(InstallPulse.renderLedger(ledger, 42))
        assertEquals(ledger, again)
        val counted = InstallPulse.counts(again, 42)
        assertEquals(1, counted.current)
        assertEquals(1, counted.total)
    }
}
