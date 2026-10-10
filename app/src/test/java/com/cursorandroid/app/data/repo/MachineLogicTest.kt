package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.Env
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class MachineLogicTest {
    private val day = MACHINE_DAY_MS
    private val now = 1_800_000_000_000L

    private val laptop = Computer(name = "Laptop", online = true, workerId = "w-1")
    private val oldBox = Computer(name = "OldBox", online = false, detail = "Seen on a previous agent")

    private fun agentOn(name: String, updated: String) = AgentSummary(
        id = "bc-$name",
        env = Env(type = "machine", name = name),
        updatedAt = updated,
    )

    @Test
    fun hiddenMachinesLeaveTheVisibleList() {
        val prefs = MachinePrefs().withMark(laptop.machineKey(), "Laptop", MachineMarkState.HIDDEN, now)
        assertEquals(listOf(oldBox), visibleMachines(listOf(laptop, oldBox), prefs, now))
        val shown = prefs.withMark(laptop.machineKey(), "Laptop", MachineMarkState.SHOWN, now)
        assertEquals(listOf(laptop, oldBox), visibleMachines(listOf(laptop, oldBox), shown, now))
    }

    @Test
    fun forgottenMachinesStayOutAndAreLabelledForgotten() {
        val prefs = MachinePrefs().withMark("id:w-1", "Laptop", MachineMarkState.FORGOTTEN, now)
        assertTrue(visibleMachines(listOf(laptop), prefs, now).isEmpty())
        assertEquals(
            MachineVisibility.Forgotten,
            machineRows(listOf(laptop), prefs, now).single().visibility,
        )
    }

    @Test
    fun aMachineThatComesBackUnderANewIdIsVisibleAgain() {
        val prefs = MachinePrefs().withMark("id:w-1", "Laptop", MachineMarkState.FORGOTTEN, now)
        val returned = laptop.copy(workerId = "w-2")
        assertEquals(listOf(returned), visibleMachines(listOf(returned), prefs, now))
    }

    @Test
    fun aNameOnlyMarkDoesNotHideAnOnlineWorkerWithAnId() {
        val prefs = MachinePrefs().withMark(oldBox.machineKey(), "OldBox", MachineMarkState.HIDDEN, now)
        assertTrue(visibleMachines(listOf(oldBox), prefs, now).isEmpty())
        val back = Computer(name = "OldBox", online = true, workerId = "w-9")
        assertEquals(listOf(back), visibleMachines(listOf(back), prefs, now))
    }

    @Test
    fun autoHideHidesOfflineMachinesNotSeenForNDaysButNeverOnlineOnes() {
        val agents = listOf(agentOn("OldBox", "2026-01-01T00:00:00Z"))
        val seen = observeMachines(MachinePrefs(), listOf(laptop, oldBox), agents, now)
        val on = seen.withAutoHideDays(30)
        assertEquals(listOf(laptop), visibleMachines(listOf(laptop, oldBox), on, now))
        assertEquals(
            MachineVisibility.AutoHidden,
            machineRows(listOf(laptop, oldBox), on, now).first { it.name == "OldBox" }.visibility,
        )
        val recent = observeMachines(
            MachinePrefs(),
            listOf(oldBox),
            listOf(agentOn("OldBox", java.time.Instant.ofEpochMilli(now - 2 * day).toString())),
            now,
        ).withAutoHideDays(30)
        assertEquals(listOf(oldBox), visibleMachines(listOf(oldBox), recent, now))
    }

    @Test
    fun autoHideOffLeavesEverythingAndUnknownLastSeenIsNeverAutoHidden() {
        val seen = observeMachines(MachinePrefs(), listOf(oldBox), emptyList(), now)
        assertEquals(listOf(oldBox), visibleMachines(listOf(oldBox), seen.withAutoHideDays(7), now))
        assertEquals(listOf(oldBox), visibleMachines(listOf(oldBox), MachinePrefs(autoHideDays = 0), now))
    }

    @Test
    fun unhidingAnAutoHiddenMachineKeepsItVisible() {
        val agents = listOf(agentOn("OldBox", "2026-01-01T00:00:00Z"))
        val base = observeMachines(MachinePrefs(), listOf(oldBox), agents, now).withAutoHideDays(30)
        assertTrue(visibleMachines(listOf(oldBox), base, now).isEmpty())
        val unhidden = base.withMark(oldBox.machineKey(), "OldBox", MachineMarkState.SHOWN, now)
        assertEquals(listOf(oldBox), visibleMachines(listOf(oldBox), unhidden, now))
    }

    @Test
    fun observeStampsOnlineWorkersAndKeepsFirstSeen() {
        val first = observeMachines(MachinePrefs(), listOf(laptop), emptyList(), now)
        val record = first.seen["id:w-1"]!!
        assertEquals(now, record.lastSeenMs)
        assertEquals(now, record.firstSeenMs)
        val later = observeMachines(first, listOf(laptop), emptyList(), now + day)
        assertEquals(now + day, later.seen["id:w-1"]!!.lastSeenMs)
        assertEquals(now, later.seen["id:w-1"]!!.firstSeenMs)
    }

    @Test
    fun aWorkerThatDisappearsStaysRememberedWithItsLastSeenTime() {
        val seen = observeMachines(MachinePrefs(), listOf(laptop), emptyList(), now)
        val rows = machineRows(emptyList(), seen, now + 3 * day)
        val row = rows.single()
        assertEquals("Laptop", row.name)
        assertFalse(row.online)
        assertEquals(now, row.lastSeenMs)
        assertEquals("Offline · last seen 3 d ago", row.statusLine(now + 3 * day))
    }

    @Test
    fun aNameOnlyEntryDropsOutOnceAWorkerWithThatNameIsOnline() {
        val first = observeMachines(MachinePrefs(), listOf(oldBox), emptyList(), now)
        assertTrue("name:oldbox" in first.seen)
        val back = Computer(name = "OldBox", online = true, workerId = "w-3")
        val next = observeMachines(first, listOf(back), emptyList(), now + day)
        assertFalse("name:oldbox" in next.seen)
        assertTrue("id:w-3" in next.seen)
    }

    @Test
    fun agentUseGivesAnOfflineMachineItsLastUsedTime() {
        val agents = listOf(
            agentOn("OldBox", "2026-03-01T10:00:00Z"),
            agentOn("OldBox", "2026-02-01T10:00:00Z"),
            agentOn("Other", "2026-04-01T10:00:00Z"),
        )
        val seen = observeMachines(MachinePrefs(), listOf(oldBox), agents, now)
        val record = seen.seen["name:oldbox"]!!
        assertEquals(java.time.Instant.parse("2026-03-01T10:00:00Z").toEpochMilli(), record.lastSeenMs)
        assertTrue(record.viaAgent)
        val line = machineRows(listOf(oldBox), seen, record.lastSeenMs + 2 * day).single()
            .statusLine(record.lastSeenMs + 2 * day, ZoneOffset.UTC)
        assertEquals("Offline · last used by an agent 2 d ago", line)
    }

    @Test
    fun rowsListOnlineFirstThenNewestAndIncludeMarkedMachinesNoLongerListed() {
        val seen = MachinePrefs(
            seen = mapOf(
                "id:old" to MachineRecord("Old", "old", lastSeenMs = now - 5 * day),
                "id:newer" to MachineRecord("Newer", "newer", lastSeenMs = now - day),
            ),
        ).withMark("id:gone", "Gone", MachineMarkState.FORGOTTEN, now)
        val rows = machineRows(listOf(laptop), seen, now)
        assertEquals(listOf("Laptop", "Newer", "Old", "Gone"), rows.map { it.name })
        assertEquals(MachineVisibility.Forgotten, rows.last().visibility)
    }

    @Test
    fun relativeTimesAndOldDatesRead() {
        assertEquals("just now", relativeSeen(now, now + 10_000))
        assertEquals("5 min ago", relativeSeen(now, now + 5 * 60_000))
        assertEquals("3 h ago", relativeSeen(now, now + 3 * 3_600_000))
        assertEquals("2026-01-01", relativeSeen(java.time.Instant.parse("2026-01-01T12:00:00Z").toEpochMilli(), now, ZoneOffset.UTC))
        assertEquals("Offline · last seen unknown", MachineRow("k", "n", null, false, false, null, null, false, MachineVisibility.Visible).statusLine(now))
    }

    @Test
    fun importAddsWhatIsMissingAndNeverOverridesLocalMarks() {
        val local = MachinePrefs().withMark("id:a", "A", MachineMarkState.SHOWN, now)
        val incoming = MachinePrefs(autoHideDays = 14)
            .withMark("id:a", "A", MachineMarkState.HIDDEN, now)
            .withMark("id:b", "B", MachineMarkState.HIDDEN, now)
        val merged = mergeMachinePrefs(local, incoming)
        assertEquals(MachineMarkState.SHOWN, merged.marks["id:a"]!!.state)
        assertEquals(MachineMarkState.HIDDEN, merged.marks["id:b"]!!.state)
        assertEquals(14, merged.autoHideDays)
        assertEquals(30, mergeMachinePrefs(local.withAutoHideDays(30), incoming).autoHideDays)
    }

    @Test
    fun theDeleteNoteSaysItOnlyForgetsLocally() {
        assertTrue(MACHINE_DELETE_NOTE.contains("on this phone only"))
        assertNull(null)
    }
}
