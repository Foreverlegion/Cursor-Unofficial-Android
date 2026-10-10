package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.auth.ApiKeyStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

class OldFormatSchemaMigrationTest {
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
        steps: List<UiMigrationStep>,
        target: Int,
    ): UiPrefsStore = UiPrefsStore.open(legacy, file, steps, target).also { open += it }

    private fun reopen(
        store: UiPrefsStore,
        legacy: LegacyUiPrefs = LegacyUiPrefs(),
        steps: List<UiMigrationStep>,
        target: Int,
    ): UiPrefsStore {
        store.close()
        open -= store
        return open(legacy, steps, target)
    }

    private val legacy = LegacyUiPrefs(
        prefs = mapOf(
            "theme_color" to 0xFF3B82F6.toInt(),
            "agent_repo_group_prefs" to
                """{"styles":{"acme/alpha":{"name":"Main","favorite":true,"color":7}},"order":["acme/alpha"]}""",
        ),
    )

    @Test
    fun missingOrUnknownSchemaVersionMigratesOnce() = runBlocking {
        val seen = mutableListOf<Int>()
        val steps = (0..3).map { from ->
            UiMigrationStep(from) { prefs, _ ->
                seen += from
                if (seen.size > 8) error("migrate re-entered at version $from")
                if (from == 0) {
                    prefs[UiKeys.themeColor] = 0xFF3B82F6.toInt()
                    prefs[UiKeys.repoGroupPrefs] =
                        """{"styles":{"acme/alpha":{"name":"Main","favorite":true,"color":7}},"order":["acme/alpha"]}"""
                }
            }
        }
        val migration = UiPrefsMigration(LegacyUiPrefs(), steps, target = 4)

        val missing = migration.migrate(emptyPreferences())
        assertEquals(listOf(0, 1, 2, 3), seen)
        assertEquals(4, missing[UiKeys.schemaVersion])
        assertEquals(0xFF3B82F6.toInt(), missing[UiKeys.themeColor])
        assertFalse(migration.shouldMigrate(missing))
        val second = migration.migrate(missing)
        assertEquals(4, second[UiKeys.schemaVersion])
        assertEquals(listOf(0, 1, 2, 3), seen)

        seen.clear()
        val gap = UiPrefsMigration(
            LegacyUiPrefs(),
            listOf(UiMigrationStep(0) { _, _ -> seen += 0 }),
            target = 3,
        )
        val gapped = gap.migrate(emptyPreferences())
        assertEquals(listOf(0), seen)
        assertEquals(3, gapped[UiKeys.schemaVersion])

        val future = emptyPreferences().toMutablePreferences().apply {
            this[UiKeys.schemaVersion] = 99
            this[UiKeys.themeColor] = 0xFF112233.toInt()
        }.toPreferences()
        assertFalse(migration.shouldMigrate(future))
        assertEquals(99, future[UiKeys.schemaVersion])
        assertEquals(0xFF112233.toInt(), future[UiKeys.themeColor])
        assertEquals(listOf(0), seen)
    }

    @Test
    fun openingAnOldPrefsFileWritesThemeAndRepoGroupOnce() {
        var calls = 0
        val steps = listOf(
            UiMigrationStep(0) { prefs, legacyPrefs ->
                calls += 1
                if (calls > 2) error("schema migration re-entered")
                schemaZeroToOne.run(prefs, legacyPrefs)
            },
            UiMigrationStep(1) { prefs, legacyPrefs ->
                calls += 1
                schemaOneToTwo.run(prefs, legacyPrefs)
            },
        )
        val ui = open(legacy, steps, target = UI_SCHEMA_VERSION)
        val chats = LocalChatStore(ui)
        assertEquals(2, calls)
        assertEquals(UI_SCHEMA_VERSION, ui.schemaVersion)
        assertEquals(0xFF3B82F6.toInt(), ui[UiKeys.themeColor])
        assertEquals("Main", chats.repoGroupPrefs.style("acme/alpha").name)

        ui.put(UiKeys.themeColor, 0xFF10B981.toInt())
        chats.repoGroupPrefs = chats.repoGroupPrefs.withStyle("acme/beta") { it.copy(name = "Side") }
        ui.flush()
        assertEquals(2, calls)

        val again = reopen(ui, legacy, steps, target = UI_SCHEMA_VERSION)
        assertEquals(2, calls)
        assertEquals(0xFF10B981.toInt(), again[UiKeys.themeColor])
        assertEquals("Side", LocalChatStore(again).repoGroupPrefs.style("acme/beta").name)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OldFormatRestoreLoopTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer
    private val pass = "correct horse".toCharArray()
    private val json = Json { ignoreUnknownKeys = true }

    private val repoGroups = RepoGroupPrefs()
        .withStyle("acme/alpha") { it.copy(name = "Main", favorite = true, color = 7) }
        .withOrder(listOf("acme/alpha"))

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = AppContainer(context)
        container.store.demoMode = false
        container.store.apiKey = null
        container.stagedRestore.discard()
    }

    private fun oldEncrypted(version: JsonElement?): String {
        val snap = SettingsSnapshot(
            themeColor = 0xFF3B82F6.toInt(),
            uiFont = "inter",
            repoGroupPrefs = repoGroups,
            githubToken = "ghp_OLD_TOKEN",
            defaultModel = "sonnet",
            notifyOnComplete = false,
        )
        val encoded = SettingsBackup.encode(buildBackup(snap, pass, 100_000))
        val fields = json.parseToJsonElement(encoded).jsonObject.toMutableMap()
        if (version == null) fields.remove("version") else fields["version"] = version
        return json.encodeToString(JsonObject(fields))
    }

    @Test
    fun oldFormatEncryptedBackupRestoreDoesNotLoop() {
        val readable = listOf(
            oldEncrypted(null),
            oldEncrypted(JsonPrimitive(0)),
            oldEncrypted(JsonPrimitive(99)),
        )
        for (text in readable) {
            val loaded = SettingsBackup.parse(text)
            assertNotNull(loaded)
            val opened = openBackup(loaded!!, pass)
            assertEquals(BackupOpen.Ok, opened.result)
            assertEquals(0xFF3B82F6.toInt(), opened.snapshot.themeColor)
            assertEquals("Main", opened.snapshot.repoGroupPrefs.style("acme/alpha").name)
            assertEquals(BackupOpen.Ok, SettingsBackup.apply(container, loaded, pass))
            assertEquals(0xFF3B82F6.toInt(), container.store.themeColor)
            assertEquals("inter", container.store.uiFont)
            assertEquals("Main", container.chats.repoGroupPrefs.style("acme/alpha").name)
            assertEquals(listOf("acme/alpha"), container.chats.repoGroupPrefs.order)
            assertEquals("sonnet", container.store.defaultModel)
            assertEquals(BackupOpen.Ok, SettingsBackup.apply(container, loaded, pass))
            assertEquals(0xFF3B82F6.toInt(), container.store.themeColor)
            assertEquals(listOf("acme/alpha"), container.chats.repoGroupPrefs.order)
        }

        val transparent = SettingsSnapshot(themeColor = 0x003B82F6, repoGroupPrefs = repoGroups)
        assertEquals(BackupOpen.Ok, SettingsBackup.apply(container, transparent, null))
        assertEquals(ApiKeyStore.DEFAULT_THEME_COLOR, container.store.themeColor)
        assertEquals("Main", container.chats.repoGroupPrefs.style("acme/alpha").name)
    }

    @Test
    fun unreadableBackupVersionDoesNotRecurse() {
        assertNull(SettingsBackup.parse(oldEncrypted(JsonNull)))
        assertNull(SettingsBackup.parse(oldEncrypted(JsonPrimitive("unknown"))))
    }
}
