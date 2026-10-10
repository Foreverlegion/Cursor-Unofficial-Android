package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.repo.GroupMove
import com.cursorandroid.app.data.repo.RepoGroupPrefs
import com.cursorandroid.app.data.repo.SettingsSnapshot
import com.cursorandroid.app.data.repo.moveGroupKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoGroupPrefsTest {
    private val alpha = "https://github.com/acme/alpha"
    private val beta = "https://github.com/acme/beta"
    private val gamma = "https://github.com/acme/gamma"

    private val agents = listOf(
        AgentSummary(id = "a", name = "A", status = "FINISHED", updatedAt = "2026-10-10T09:00:00Z"),
        AgentSummary(id = "b", name = "B", status = "FINISHED", updatedAt = "2026-10-09T09:00:00Z"),
        AgentSummary(id = "c", name = "C", status = "FINISHED", updatedAt = "2026-10-08T09:00:00Z"),
    )
    private val git = mapOf(
        "a" to GitSnap("a", repoUrl = alpha),
        "b" to GitSnap("b", repoUrl = beta),
        "c" to GitSnap("c", repoUrl = gamma),
    )

    private fun keys(prefs: RepoGroupPrefs) = repoGroups(agents, git, emptySet(), prefs).map { it.key }

    @Test
    fun defaultOrderIsMostRecentActivity() {
        assertEquals(listOf("acme/alpha", "acme/beta", "acme/gamma"), keys(RepoGroupPrefs()))
    }

    @Test
    fun manualOrderOverridesActivityAndResetRestoresIt() {
        val manual = RepoGroupPrefs().withOrder(listOf("acme/gamma", "acme/alpha", "acme/beta"))
        assertEquals(listOf("acme/gamma", "acme/alpha", "acme/beta"), keys(manual))
        assertEquals(listOf("acme/alpha", "acme/beta", "acme/gamma"), keys(manual.resetOrder()))
    }

    @Test
    fun groupsMissingFromManualOrderGoLastInActivityOrder() {
        val manual = RepoGroupPrefs().withOrder(listOf("acme/gamma"))
        assertEquals(listOf("acme/gamma", "acme/alpha", "acme/beta"), keys(manual))
    }

    @Test
    fun favoritesPinAboveManualOrder() {
        val prefs = RepoGroupPrefs()
            .withOrder(listOf("acme/alpha", "acme/beta", "acme/gamma"))
            .withStyle("acme/gamma") { it.copy(favorite = true) }
        val groups = repoGroups(agents, git, emptySet(), prefs)
        assertEquals(listOf("acme/gamma", "acme/alpha", "acme/beta"), groups.map { it.key })
        assertTrue(groups.first().favorite)
    }

    @Test
    fun customNameKeepsRealPathAndColorIsCarried() {
        val prefs = RepoGroupPrefs()
            .withStyle("acme/beta") { it.copy(name = "  Work  ", color = 0xFF3B82F6.toInt()) }
        val beta = repoGroups(agents, git, emptySet(), prefs).first { it.key == "acme/beta" }
        assertEquals("Work", beta.title)
        assertEquals("beta", beta.label)
        assertEquals("acme/beta", beta.path)
        assertEquals(0xFF3B82F6.toInt(), beta.color)
    }

    @Test
    fun resettingEverythingPrunesTheStyle() {
        val prefs = RepoGroupPrefs()
            .withStyle("acme/beta") { it.copy(name = "Work", favorite = true, color = 5) }
            .withStyle("acme/beta") { it.copy(name = "   ", favorite = false, color = 0) }
        assertTrue(prefs.isEmpty)
        assertFalse(prefs.styles.containsKey("acme/beta"))
    }

    @Test
    fun moveWorksInsideTheFavoriteSection() {
        val order = listOf("f1", "f2", "n1", "n2", "n3")
        val favs = setOf("f1", "f2")
        assertEquals(listOf("f2", "f1", "n1", "n2", "n3"), moveGroupKey(order, favs, "f2", GroupMove.Up))
        assertEquals(listOf("f1", "f2", "n1", "n2", "n3"), moveGroupKey(order, favs, "f1", GroupMove.Up))
        assertEquals(listOf("f1", "f2", "n1", "n3", "n2"), moveGroupKey(order, favs, "n2", GroupMove.Down))
        assertEquals(listOf("f1", "f2", "n3", "n1", "n2"), moveGroupKey(order, favs, "n3", GroupMove.Top))
        assertEquals(order, moveGroupKey(order, favs, "n3", GroupMove.Down))
        assertEquals(order, moveGroupKey(order, favs, "missing", GroupMove.Top))
    }

    @Test
    fun snapshotRoundTripsGroupPrefsAndOldExportsLoadEmpty() {
        val json = Json { ignoreUnknownKeys = true }
        val prefs = RepoGroupPrefs()
            .withStyle("acme/alpha") { it.copy(name = "Main", favorite = true, color = 7) }
            .withOrder(listOf("acme/beta", "acme/alpha"))
        val again = json.decodeFromString<SettingsSnapshot>(
            json.encodeToString(SettingsSnapshot.serializer(), SettingsSnapshot(repoGroupPrefs = prefs)),
        )
        assertEquals(prefs, again.repoGroupPrefs)
        assertTrue(json.decodeFromString<SettingsSnapshot>("""{"version":1}""").repoGroupPrefs.isEmpty)
    }
}
