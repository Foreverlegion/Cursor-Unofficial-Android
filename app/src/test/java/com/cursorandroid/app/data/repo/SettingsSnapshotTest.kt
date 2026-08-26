package com.cursorandroid.app.data.repo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSnapshotTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun oldChatMetaIsNotHidden() {
        val meta = json.decodeFromString<ChatMeta>("""{"title":"Old"}""")
        assertEquals(false, meta.hidden)
        assertEquals(false, meta.favorite)
    }

    @Test
    fun oldExportGetsDefaultAppearance() {
        val snap = json.decodeFromString<SettingsSnapshot>("""{"version":1}""")
        assertEquals(0xFFF54E00.toInt(), snap.themeColor)
        assertTrue(snap.showInboxEnvs)
        assertTrue(snap.showInboxRemote)
        assertEquals(false, snap.autoUpdate)
    }

    @Test
    fun appearanceRoundTrips() {
        val snap = SettingsSnapshot(
            themeColor = 0xFF3B82F6.toInt(),
            showInboxEnvs = false,
            showInboxRemote = true,
            autoUpdate = true,
        )
        val again = json.decodeFromString<SettingsSnapshot>(json.encodeToString(SettingsSnapshot.serializer(), snap))
        assertEquals(0xFF3B82F6.toInt(), again.themeColor)
        assertEquals(false, again.showInboxEnvs)
        assertEquals(true, again.showInboxRemote)
        assertEquals(true, again.autoUpdate)
    }
}
