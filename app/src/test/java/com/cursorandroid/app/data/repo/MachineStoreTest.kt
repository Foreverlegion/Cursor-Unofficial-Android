package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.Computer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MachineStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var file: File
    private val open = mutableListOf<UiPrefsStore>()
    private var clock = 1_800_000_000_000L

    private val laptop = Computer(name = "Laptop", online = true, workerId = "w-1")
    private val stale = Computer(name = "OldBox", online = false)

    @Before
    fun setUp() {
        file = File(tmp.newFolder(), "ui_prefs.preferences_pb")
    }

    @After
    fun tearDown() {
        open.forEach { runCatching { it.close() } }
    }

    private fun ui(target: Int = UI_SCHEMA_VERSION, steps: List<UiMigrationStep> = uiMigrationSteps): UiPrefsStore =
        UiPrefsStore.open(LegacyUiPrefs(), file, steps, target).also { open += it }

    private fun reopen(store: UiPrefsStore, target: Int = UI_SCHEMA_VERSION): UiPrefsStore {
        store.close()
        open -= store
        return ui(target)
    }

    private fun machines(store: UiPrefsStore) = MachineStore(store) { clock }

    @Test
    fun hideForgetAndAutoHideSurviveARestart() {
        val first = ui()
        val m = machines(first)
        m.observe(listOf(laptop, stale), emptyList())
        m.hide("id:w-1", "Laptop")
        m.forget("name:oldbox", "OldBox")
        m.setAutoHideDays(30)

        val second = machines(reopen(first))
        val prefs = second.prefs()
        assertEquals(MachineMarkState.HIDDEN, prefs.marks["id:w-1"]!!.state)
        assertEquals(MachineMarkState.FORGOTTEN, prefs.marks["name:oldbox"]!!.state)
        assertEquals(30, prefs.autoHideDays)
        assertEquals(clock, prefs.seen["id:w-1"]!!.lastSeenMs)
        assertTrue(second.visible(listOf(laptop, stale)).isEmpty())
    }

    @Test
    fun showBringsAMachineBackAndPersists() {
        val first = ui()
        val m = machines(first)
        m.hide("id:w-1", "Laptop")
        m.show("id:w-1", "Laptop")
        val again = machines(reopen(first))
        assertEquals(listOf(laptop), again.visible(listOf(laptop)))
    }

    @Test
    fun updatingFromSchemaOneKeepsEverythingAndNeverResetsMachines() {
        val v1 = ui(target = 1, steps = listOf(schemaZeroToOne))
        assertNull(v1[UiKeys.machinePrefs])
        val older = MachinePrefs().withMark("id:w-1", "Laptop", MachineMarkState.HIDDEN, clock).withAutoHideDays(14)
        v1.put(UiKeys.machinePrefs, kotlinx.serialization.json.Json.encodeToString(MachinePrefs.serializer(), older))
        LocalChatStore(v1).setPinned("agent-1", true)

        val v2 = reopen(v1, target = 2)
        assertEquals(2, v2.schemaVersion)
        val kept = machines(v2).prefs()
        assertEquals(MachineMarkState.HIDDEN, kept.marks["id:w-1"]!!.state)
        assertEquals(14, kept.autoHideDays)
        assertTrue(LocalChatStore(v2).meta("agent-1").pinned)
    }

    @Test
    fun aFirstRunOnSchemaTwoStartsEmptyWithoutLosingOtherData() {
        val v1 = ui(target = 1, steps = listOf(schemaZeroToOne))
        LocalChatStore(v1).setPinned("agent-2", true)
        val v2 = reopen(v1, target = 2)
        assertEquals("{}", v2[UiKeys.machinePrefs])
        assertTrue(machines(v2).prefs().isEmpty)
        assertTrue(LocalChatStore(v2).meta("agent-2").pinned)
    }

    @Test
    fun aNewerSchemaOnDiskKeepsItsMachineDataWhenOpenedByThisBuild() {
        val future = ui(target = 5, steps = uiMigrationSteps)
        machines(future).hide("id:w-1", "Laptop")
        val older = reopen(future, target = UI_SCHEMA_VERSION)
        assertEquals(5, older.schemaVersion)
        assertEquals(MachineMarkState.HIDDEN, machines(older).prefs().marks["id:w-1"]!!.state)
    }

    @Test
    fun unreadableMachineDataIsKeptAsideNotLost() {
        val store = ui()
        store.put(UiKeys.machinePrefs, "{not json")
        val m = machines(store)
        assertTrue(m.prefs().isEmpty)
        assertEquals("{not json", store[UiKeys.machinePrefsUnreadable])
        m.hide("id:x", "X")
        assertNotNull(m.prefs().marks["id:x"])
        assertEquals("{not json", store[UiKeys.machinePrefsUnreadable])
    }

    @Test
    fun observingAnEmptyListingChangesNothing() {
        val store = ui()
        val m = machines(store)
        m.observe(listOf(laptop), emptyList())
        val before = m.prefs()
        m.observe(emptyList(), emptyList())
        assertEquals(before, m.prefs())
    }

    @Test
    fun unknownFieldsFromALaterBuildAreIgnoredNotFatal() {
        val store = ui()
        store.put(UiKeys.machinePrefs, """{"auto_hide_days":7,"future_field":1,"marks":{"id:z":{"state":"hidden","extra":true}}}""")
        val prefs = machines(store).prefs()
        assertEquals(7, prefs.autoHideDays)
        assertEquals(MachineMarkState.HIDDEN, prefs.marks["id:z"]!!.state)
    }
}
