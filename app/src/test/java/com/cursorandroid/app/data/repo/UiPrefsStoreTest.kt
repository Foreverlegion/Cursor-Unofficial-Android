package com.cursorandroid.app.data.repo

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UiPrefsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var file: File
    private val open = mutableListOf<UiPrefsStore>()

    @Before
    fun setUp() {
        file = File(tmp.newFolder(), "ui_prefs.preferences_pb")
    }

    @After
    fun tearDown() {
        open.forEach { runCatching { it.close() } }
    }

    private fun open(
        legacy: LegacyUiPrefs = LegacyUiPrefs(),
        steps: List<UiMigrationStep> = uiMigrationSteps,
        target: Int = UI_SCHEMA_VERSION,
    ): UiPrefsStore = UiPrefsStore.open(legacy, file, steps, target).also { open += it }

    private fun reopen(
        store: UiPrefsStore,
        legacy: LegacyUiPrefs = LegacyUiPrefs(),
        steps: List<UiMigrationStep> = uiMigrationSteps,
        target: Int = UI_SCHEMA_VERSION,
    ): UiPrefsStore {
        store.close()
        open -= store
        return open(legacy, steps, target)
    }

    private val legacyChatMeta = """{"agent-1":{"title":"Keep","favorite":true,"favoritedAt":5,"pinned":true,"pinnedAt":9}}"""

    private val legacy = LegacyUiPrefs(
        prefs = mapOf(
            "agent_group_by_repo" to false,
            "agent_compact_cards" to false,
            "agent_hide_finished_days" to 3,
            "agent_collapsed_repos" to setOf("acme/alpha", "acme/beta"),
            "agent_repo_group_prefs" to
                """{"styles":{"acme/alpha":{"name":"Main","favorite":true,"color":7}},"order":["acme/beta","acme/alpha"]}""",
            "show_inbox_envs" to false,
            "theme_color" to 0xFF3B82F6.toInt(),
            "ui_font" to "inter",
            "code_font" to "jetbrains_mono",
            "text_scale_pct" to 120,
            "chat_density" to "compact",
            "notify_on_complete" to false,
        ),
        chatMetaJson = legacyChatMeta,
    )

    @Test
    fun firstOpenImportsEverythingFromTheOldPreferences() {
        val ui = open(legacy)
        assertEquals(UI_SCHEMA_VERSION, ui.schemaVersion)
        val chats = LocalChatStore(ui)
        assertFalse(chats.groupByRepo)
        assertFalse(chats.compactCards)
        assertEquals(3, chats.hideFinishedDays)
        assertEquals(setOf("acme/alpha", "acme/beta"), chats.collapsedRepos)
        assertEquals(listOf("acme/beta", "acme/alpha"), chats.repoGroupPrefs.order)
        assertEquals("Main", chats.repoGroupPrefs.style("acme/alpha").name)
        assertTrue(chats.repoGroupPrefs.style("acme/alpha").favorite)
        val meta = chats.meta("agent-1")
        assertTrue(meta.pinned)
        assertEquals(9L, meta.pinnedAt)
        assertEquals("Keep", meta.title)
        assertEquals(false, ui[UiKeys.showInboxEnvs])
        assertEquals(0xFF3B82F6.toInt(), ui[UiKeys.themeColor])
        assertEquals("inter", ui[UiKeys.uiFont])
        assertEquals("jetbrains_mono", ui[UiKeys.codeFont])
        assertEquals(120, ui[UiKeys.textScalePct])
        assertEquals("compact", ui[UiKeys.chatDensity])
        assertNull(ui[stringPreferencesKey("notify_on_complete")])
    }

    @Test
    fun freshInstallGetsDefaultsAndTheCurrentVersion() {
        val ui = open()
        val chats = LocalChatStore(ui)
        assertEquals(UI_SCHEMA_VERSION, ui.schemaVersion)
        assertTrue(chats.groupByRepo)
        assertTrue(chats.compactCards)
        assertEquals(0, chats.hideFinishedDays)
        assertTrue(chats.collapsedRepos.isEmpty())
        assertTrue(chats.repoGroupPrefs.isEmpty)
        assertTrue(chats.snapshot().isEmpty())
    }

    @Test
    fun everythingSurvivesClosingAndReopeningTheFile() {
        val first = open()
        val chats = LocalChatStore(first)
        chats.collapsedRepos = setOf("acme/alpha")
        chats.repoGroupPrefs = RepoGroupPrefs()
            .withStyle("acme/alpha") { it.copy(name = "Main", favorite = true, color = 0xFFE53935.toInt()) }
            .withOrder(listOf("acme/beta", "acme/alpha"))
        chats.setPinned("agent-2", true)
        chats.setFavorite("agent-3", true)
        chats.setTitle("agent-3", "Renamed")
        chats.groupByRepo = false
        chats.compactCards = false
        chats.hideFinishedDays = 7
        chats.inboxShowArchived = true
        first.put(UiKeys.themeColor, 0xFF10B981.toInt())
        first.put(UiKeys.uiFont, "serif")
        first.put(UiKeys.textScalePct, 135)

        val second = reopen(first)
        val again = LocalChatStore(second)
        assertEquals(setOf("acme/alpha"), again.collapsedRepos)
        assertEquals("Main", again.repoGroupPrefs.style("acme/alpha").name)
        assertEquals(0xFFE53935.toInt(), again.repoGroupPrefs.style("acme/alpha").color)
        assertEquals(listOf("acme/beta", "acme/alpha"), again.repoGroupPrefs.order)
        assertTrue(again.meta("agent-2").pinned)
        assertTrue(again.isFavorite("agent-3"))
        assertEquals("Renamed", again.title("agent-3"))
        assertFalse(again.groupByRepo)
        assertFalse(again.compactCards)
        assertEquals(7, again.hideFinishedDays)
        assertTrue(again.inboxShowArchived)
        assertEquals(0xFF10B981.toInt(), second[UiKeys.themeColor])
        assertEquals("serif", second[UiKeys.uiFont])
        assertEquals(135, second[UiKeys.textScalePct])
    }

    @Test
    fun oldPreferencesNeverOverwriteNewerValuesOnLaterStarts() {
        val first = open(legacy)
        LocalChatStore(first).hideFinishedDays = 1
        first.put(UiKeys.uiFont, "serif")
        val second = reopen(first, legacy)
        assertEquals(1, LocalChatStore(second).hideFinishedDays)
        assertEquals("serif", second[UiKeys.uiFont])
    }

    @Test
    fun unflushedWritesAreStillSavedWhenTheStoreCloses() {
        val first = open()
        repeat(25) { LocalChatStore(first).setPinned("agent-$it", true) }
        val second = reopen(first)
        assertEquals(25, LocalChatStore(second).snapshot().count { it.value.pinned })
    }

    @Test
    fun updateToANewSchemaKeepsDataAndRunsOnlyTheNewStep() {
        var zeroRuns = 0
        val countingZero = UiMigrationStep(0) { p, l ->
            zeroRuns += 1
            schemaZeroToOne.run(p, l)
        }
        val v1 = open(legacy, listOf(countingZero), target = 1)
        LocalChatStore(v1).setPinned("agent-9", true)
        assertEquals(1, zeroRuns)

        val newKey = intPreferencesKey("agent_card_corner")
        val one = UiMigrationStep(1) { p, _ ->
            p[newKey] = 16
            val old = p[UiKeys.textScalePct]
            if (old != null) p[UiKeys.textScalePct] = old + 5
        }
        val v2 = reopen(v1, LegacyUiPrefs(), listOf(countingZero, one), target = 2)
        assertEquals(1, zeroRuns)
        assertEquals(2, v2.schemaVersion)
        assertEquals(16, v2[newKey])
        assertEquals(125, v2[UiKeys.textScalePct])
        val chats = LocalChatStore(v2)
        assertTrue(chats.meta("agent-9").pinned)
        assertEquals("Main", chats.repoGroupPrefs.style("acme/alpha").name)
        assertEquals(setOf("acme/alpha", "acme/beta"), chats.collapsedRepos)
    }

    @Test
    fun appSkippingSeveralReleasesWalksEveryStepInOrder() {
        val order = mutableListOf<Int>()
        val steps = (0..2).map { from -> UiMigrationStep(from) { _, _ -> order += from } }
        val ui = open(LegacyUiPrefs(), steps, target = 3)
        assertEquals(listOf(0, 1, 2), order)
        assertEquals(3, ui.schemaVersion)
    }

    @Test
    fun aNewerSchemaOnDiskIsNeverDowngradedOrCleared() {
        val future = open(legacy, emptyList(), target = 5)
        LocalChatStore(future).setPinned("agent-7", true)
        val older = reopen(future, legacy, uiMigrationSteps, target = UI_SCHEMA_VERSION)
        assertEquals(5, older.schemaVersion)
        assertTrue(LocalChatStore(older).meta("agent-7").pinned)
    }

    @Test
    fun unreadableChatMetaIsKeptAside_notWiped() {
        val ui = open()
        ui.put(UiKeys.chatMeta, "{not json")
        val chats = LocalChatStore(ui)
        assertTrue(chats.snapshot().isEmpty())
        assertEquals("{not json", ui[UiKeys.chatMetaUnreadable])
    }

    @Test
    fun corruptFileFallsBackToTheOldPreferencesInsteadOfCrashing() {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
        val ui = open(legacy)
        assertNotNull(ui[UiKeys.themeColor])
        assertEquals(UI_SCHEMA_VERSION, ui.schemaVersion)
        assertTrue(LocalChatStore(ui).meta("agent-1").pinned)
    }
}

/**
 * The serialized names are the stored format. R8 can rename fields, so each one is pinned with
 * `@SerialName`. These strings must not change.
 */
class PersistedJsonKeysTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun chatMetaKeysAreStable() {
        val text = json.encodeToString(ChatMeta(title = "t", pinned = true, pinnedAt = 2))
        assertEquals(
            """{"title":"t","favorite":false,"favoritedAt":0,"hidden":false,"muted":false,"repoUrl":null,""" +
                """"baseBranch":null,"startSha":null,"ignoredRemoteSha":null,"pinned":true,"pinnedAt":2,"openFinishedPr":false}""",
            text,
        )
    }

    @Test
    fun repoGroupKeysAreStable() {
        val prefs = RepoGroupPrefs()
            .withStyle("acme/alpha") { it.copy(name = "Main", favorite = true, color = 7) }
            .withOrder(listOf("acme/alpha"))
        assertEquals(
            """{"styles":{"acme/alpha":{"name":"Main","favorite":true,"color":7}},"order":["acme/alpha"]}""",
            json.encodeToString(prefs),
        )
    }

    @Test
    fun storedFilesFromEarlierAndLaterBuildsStillDecode() {
        val older = json.decodeFromString<ChatMeta>("""{"title":"x"}""")
        assertEquals("x", older.title)
        assertFalse(older.pinned)
        val newer = json.decodeFromString<RepoGroupPrefs>(
            """{"styles":{"a":{"name":"n","shape":"round"}},"order":["a"],"extra":1}""",
        )
        assertEquals("n", newer.style("a").name)
        assertEquals(listOf("a"), newer.order)
    }
}
