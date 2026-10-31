package com.cursorandroid.app.data.repo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppearanceSettingsPersistenceTest {
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

    @Test
    fun homeDensitySurvivesReopen() {
        val ui = UiPrefsStore.open(LegacyUiPrefs(), file).also { open += it }
        ui.put(UiKeys.homeDensity, "large")
        ui.flush()
        val again = UiPrefsStore.open(LegacyUiPrefs(), file).also { open += it }
        assertEquals("large", again[UiKeys.homeDensity])
    }

    @Test
    fun builtInFolderIconsStillAccepted() {
        val ui = UiPrefsStore.open(LegacyUiPrefs(), file).also { open += it }
        val folders = LocalFolderStore(ui, newId = { "fld-1" })
        val created = folders.create("Demo")!!
        LocalFolderStore.ICON_CHOICES.forEach { icon ->
            assertTrue("missing built-in icon $icon", icon in LocalFolderStore.ICON_IDS)
            folders.setIcon(created.id, icon)
            assertEquals(icon, folders.snapshot().folders.single().icon)
        }
    }

    @Test
    fun customFolderIconRefPersists() {
        val ui = UiPrefsStore.open(LegacyUiPrefs(), file).also { open += it }
        val folders = LocalFolderStore(ui, newId = { "fld-1" })
        val created = folders.create("Custom")!!
        folders.setIcon(created.id, "${CUSTOM_FOLDER_ICON_PREFIX}fld-1.webp")
        ui.flush()
        val again = LocalFolderStore(UiPrefsStore.open(LegacyUiPrefs(), file).also { open += it })
        assertEquals("${CUSTOM_FOLDER_ICON_PREFIX}fld-1.webp", again.snapshot().folders.single().icon)
    }
}
