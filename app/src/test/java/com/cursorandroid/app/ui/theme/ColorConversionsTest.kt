package com.cursorandroid.app.ui.theme

import com.cursorandroid.app.data.repo.FolderMembership
import com.cursorandroid.app.data.repo.LocalFolder
import com.cursorandroid.app.data.repo.LocalFolderStore
import com.cursorandroid.app.data.repo.SettingsSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorConversionsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun hsvHexRoundTripForPresetsAndCustom() {
        val samples = ThemeColorPresets + LocalFolderStore.NAME_COLORS + listOf(0xFF00FF00.toInt(), 0xFF336699.toInt())
        for (sample in samples) {
            assertTrue("round trip $sample", hsvRoundTripCloseEnough(sample))
        }
    }

    @Test
    fun hsvMatchesKnownRed() {
        val hsv = argbToHsv(0xFFFF0000.toInt())
        assertEquals(0f, hsv.hue, 0.5f)
        assertEquals(1f, hsv.saturation, 0.02f)
        assertEquals(1f, hsv.value, 0.02f)
    }

    @Test
    fun wheelCenterIsNeutralAtLowSaturation() {
        val picked = hsFromWheelOffset(50f, 50f, 40f, 50f, 50f)
        assertEquals(0f, picked?.saturation ?: -1f, 0.02f)
    }

    @Test
    fun folderCustomColorSurvivesSnapshotJson() {
        val snap = SettingsSnapshot(
            themeColor = 0xFF123456.toInt(),
            folders = FolderMembership(
                folders = listOf(
                    LocalFolder(
                        id = "fld-a",
                        name = "Custom",
                        createdAt = 1,
                        color = 0xFF00FF00.toInt(),
                    ),
                ),
            ),
        )
        val again = json.decodeFromString<SettingsSnapshot>(
            json.encodeToString(SettingsSnapshot.serializer(), snap),
        )
        assertEquals(0xFF00FF00.toInt(), again.folders?.folders?.single()?.color)
        assertEquals(0xFF123456.toInt(), again.themeColor)
    }
}
