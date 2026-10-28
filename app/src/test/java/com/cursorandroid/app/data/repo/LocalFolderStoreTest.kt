package com.cursorandroid.app.data.repo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalFolderStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var file: File
    private val open = mutableListOf<UiPrefsStore>()
    private var ids = 0

    @Before
    fun setUp() {
        file = File(tmp.newFolder(), "ui_prefs.preferences_pb")
    }

    @After
    fun tearDown() {
        open.forEach { runCatching { it.close() } }
    }

    @Test
    fun foldersAndPendingArchivesSurviveTheSchemaTwoToThreeUpgrade() {
        val v2 = ui(target = 2, steps = listOf(schemaZeroToOne, schemaOneToTwo))
        val folders = store(v2)
        val created = folders.create("Thermal Nexus")
        assertEquals("fld-1", created?.id)
        folders.setFavorite("fld-1", true)
        folders.setCollapsed("fld-1", true)
        folders.moveGroup("acme/thermal-nexus", "fld-1")
        folders.moveChats(listOf("chat-1"), "fld-1")
        LocalChatStore(v2).setPinned("chat-1", true)
        PendingArchiveStore(v2).save(
            listOf(PendingArchive(agentId = "chat-1", previousStatus = "FINISHED", requestedAt = 1, dueAt = 8)),
        )

        val v3 = reopen(v2)
        assertEquals(3, v3.schemaVersion)
        val snap = LocalFolderStore(v3).snapshot()
        assertEquals("Thermal Nexus", snap.folders.single().name)
        assertTrue(snap.folders.single().favorite)
        assertTrue(snap.folders.single().collapsed)
        assertEquals("fld-1", snap.groups["acme/thermal-nexus"])
        assertEquals("fld-1", snap.chats["chat-1"])
        assertTrue(LocalChatStore(v3).meta("chat-1").pinned)
        assertEquals("FINISHED", PendingArchiveStore(v3).load().single().previousStatus)
    }

    @Test
    fun migrationSeedDoesNotReplaceRecordsWrittenBeforeTheStep() {
        val v2 = ui(target = 2, steps = listOf(schemaZeroToOne, schemaOneToTwo))
        v2.put(
            UiKeys.agentFolders,
            """{"folders":[{"id":"fld-keep","name":"Keep","createdAt":1,"collapsed":false,"favorite":true}],"chats":{"a":"fld-keep"},"groups":{"acme/app":"fld-keep"}}""",
        )
        v2.put(
            UiKeys.pendingArchives,
            """[{"agentId":"a","previousStatus":"RUNNING","requestedAt":1,"dueAt":2,"sending":true}]""",
        )
        v2.flush()

        val v3 = reopen(v2)
        val snap = LocalFolderStore(v3).snapshot()
        assertEquals("Keep", snap.folders.single().name)
        assertEquals(0, snap.folders.single().color)
        assertEquals("", snap.folders.single().icon)
        assertEquals("fld-keep", snap.chats["a"])
        assertEquals("fld-keep", snap.groups["acme/app"])
        val pending = PendingArchiveStore(v3).load().single()
        assertEquals("a", pending.agentId)
        assertEquals("RUNNING", pending.previousStatus)
        assertTrue(pending.sending)
    }

    @Test
    fun deleteUngroupsAndBlankOrDuplicateNamesAreRejected() {
        val folders = store(ui())
        assertNull(folders.create("  "))
        assertEquals("fld-1", folders.create("Thermal Nexus")?.id)
        assertNull(folders.create("thermal nexus"))
        assertFalse(folders.rename("fld-1", " "))
        val other = folders.create("Other")
        assertEquals("fld-2", other?.id)
        assertFalse(folders.rename("fld-2", "THERMAL NEXUS"))
        folders.moveChats(listOf("c"), "fld-1")
        folders.moveGroup("acme/app", "fld-1")
        folders.delete("fld-1")
        val snap = folders.snapshot()
        assertEquals(listOf("fld-2"), snap.folders.map { it.id })
        assertTrue(snap.chats.isEmpty())
        assertTrue(snap.groups.isEmpty())
    }

    @Test
    fun colorAndIconSurviveReopenAndUnknownValuesAreRejected() {
        val first = store(ui())
        first.create("Thermal Nexus")
        first.setColor("fld-1", 0xFF00FF00.toInt())
        first.setIcon("fld-1", "nope")
        assertEquals(0xFF00FF00.toInt(), first.snapshot().folders.single().color)
        assertEquals("", first.snapshot().folders.single().icon)
        first.setColor("fld-1", LocalFolderStore.NAME_COLORS[0])
        first.setIcon("fld-1", "code")
        val second = LocalFolderStore(reopen(open.single()))
        assertEquals(LocalFolderStore.NAME_COLORS[0], second.snapshot().folders.single().color)
        assertEquals("code", second.snapshot().folders.single().icon)
        assertEquals(LocalFolderStore.ICON_CHOICES.toSet(), LocalFolderStore.ICON_IDS)
    }

    @Test
    fun aReopenKeepsFoldersCreatedOnTheCurrentSchema() {
        val first = store(ui())
        first.create("Thermal Nexus")
        first.moveGroup("gitlab/thermal-nexus", "fld-1")
        val second = LocalFolderStore(reopen(open.single()))
        assertEquals("Thermal Nexus", second.snapshot().folders.single().name)
        assertEquals("fld-1", second.snapshot().groups["gitlab/thermal-nexus"])
    }

    private fun store(prefs: UiPrefsStore): LocalFolderStore {
        return LocalFolderStore(
            prefs,
            newId = { "fld-${++ids}" },
            now = { 10L },
        )
    }

    private fun ui(target: Int = UI_SCHEMA_VERSION, steps: List<UiMigrationStep> = uiMigrationSteps): UiPrefsStore =
        UiPrefsStore.open(LegacyUiPrefs(), file, steps, target).also { open += it }

    private fun reopen(store: UiPrefsStore, target: Int = UI_SCHEMA_VERSION): UiPrefsStore {
        store.close()
        open -= store
        return ui(target = target)
    }
}
