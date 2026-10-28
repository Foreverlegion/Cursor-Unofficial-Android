package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.ui.theme.HomeDensity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeAppearanceSettingsTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        container = AppContainer(context)
    }

    @Test
    fun homeDensityDefaultsAndPersists() {
        assertEquals("comfortable", container.store.homeDensity)
        container.store.homeDensity = HomeDensity.Large.id
        assertEquals(HomeDensity.Large.id, container.store.homeDensity)
        container.ui.flush()
    }

    @Test
    fun snapshotRoundTripsHomeDensityFontsAndCustomIconRefs() {
        container.store.homeDensity = HomeDensity.Compact.id
        container.store.uiFont = ImportedFontStore.PREFIX + "abc123"
        container.importedFonts.replaceAll(
            listOf(ImportedFontRecord(id = "abc123", label = "Mine", fileName = "abc123.ttf")),
        )
        container.folders.replace(
            FolderMembership(
                folders = listOf(
                    LocalFolder(id = "fld-x", name = "Mine", createdAt = 1, icon = FolderIconStore.PREFIX + "fld-x"),
                ),
            ),
        )
        container.folderIcons.restoreFromBackup(mapOf("fld-x" to android.util.Base64.encodeToString(byteArrayOf(1, 2, 3), android.util.Base64.NO_WRAP)))

        val snap = SettingsBackup.capture(container)
        val text = SettingsBackup.encode(snap)
        val decoded = json.decodeFromString<SettingsSnapshot>(text)
        assertEquals(HomeDensity.Compact.id, decoded.homeDensity)
        assertEquals(ImportedFontStore.PREFIX + "abc123", decoded.uiFont)
        assertEquals("abc123", decoded.importedFonts.single().id)
        assertTrue(decoded.customFolderIcons.containsKey("fld-x"))
        assertEquals(FolderIconStore.PREFIX + "fld-x", decoded.folders?.folders?.single()?.icon)
    }

    @Test
    fun oldSnapshotKeepsDefaultHomeDensity() {
        val snap = json.decodeFromString<SettingsSnapshot>("""{"version":2,"themeColor":0}""")
        assertEquals("comfortable", snap.homeDensity)
        assertTrue(snap.importedFonts.isEmpty())
        assertTrue(snap.customFolderIcons.isEmpty())
    }
}
