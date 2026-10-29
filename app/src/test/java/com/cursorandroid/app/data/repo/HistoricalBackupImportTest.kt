package com.cursorandroid.app.data.repo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One fixture for each backup shape that shipped. Patch releases that wrote the same JSON share a file.
 * 1.0.30 was never a separate build; 1.0.31 used the 1.0.29 shape.
 */
class HistoricalBackupImportTest {
    private val strict = Json { ignoreUnknownKeys = false }

    @Test
    fun everyShippedShapeParsesAndKeepsKnownFields() {
        for (sample in shapes) {
            val text = read(sample.file)
            assertTrue(
                sample.file,
                runCatching { strict.decodeFromString(SettingsSnapshot.serializer(), text) }.isFailure,
            )
            val snap = SettingsBackup.parse(text)
            assertTrue(sample.file, snap != null)
            snap!!
            assertEquals("${sample.versions} ${sample.file}", sample.model, snap.defaultModel)
            assertEquals(sample.file, sample.fileVersion, snap.version)
            assertNull(sample.file, snap.apiKey)
            assertNull(sample.file, snap.folders)
            assertNull(sample.file, snap.collapsedRepos)
            assertNull(sample.file, snap.secrets)
            sample.check(snap)
        }
    }

    @Test
    fun nullsAndMissingNestedFieldsUseDefaults() {
        val snap = SettingsBackup.parse(
            """
            {
              "version": 1,
              "defaultModel": "partial",
              "notifyOnComplete": null,
              "showMicrophone": null,
              "textScalePct": null,
              "chats": {"c": {"title": "kept", "hidden": null, "extraChat": true}},
              "conversations": {"c": [{"text": "only text", "mystery": true}]},
              "drafts": {"c": {"text": "d", "attaches": [{"mime": "text/plain"}], "modelParams": [{"extra": true}]}},
              "repoDefaults": [{"modelId": "opus", "extra": 1}],
              "machinePrefs": {
                "seen": {"k": {"last_seen_ms": 2, "extra": true}},
                "marks": {"k": {"at_ms": 4}}
              },
              "pendingArchives": [{"agentId": "sneaky"}],
              "notAField": {"nested": true}
            }
            """.trimIndent(),
        )
        assertTrue(snap != null)
        snap!!
        assertEquals("partial", snap.defaultModel)
        assertTrue(snap.notifyOnComplete)
        assertTrue(snap.showMicrophone)
        assertEquals(100, snap.textScalePct)
        assertEquals("kept", snap.chats["c"]?.title)
        assertFalse(snap.chats["c"]?.hidden == true)
        assertEquals("only text", snap.conversations["c"]?.single()?.text)
        assertEquals("", snap.conversations["c"]?.single()?.id)
        assertEquals("", snap.drafts["c"]?.attaches?.single()?.name)
        assertEquals("", snap.drafts["c"]?.modelParams?.single()?.id)
        assertEquals("", snap.repoDefaults.single().repoUrl)
        assertEquals("opus", snap.repoDefaults.single().modelId)
        assertEquals("", snap.machinePrefs.seen["k"]?.name)
        assertEquals(2L, snap.machinePrefs.seen["k"]?.lastSeenMs)
        assertEquals("", snap.machinePrefs.marks["k"]?.state)
        assertNull(snap.folders)
        assertNull(snap.apiKey)
    }

    private fun read(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!.bufferedReader().use { it.readText() }

    private class Shape(
        val file: String,
        val versions: String,
        val model: String,
        val fileVersion: Int,
        val check: (SettingsSnapshot) -> Unit,
    )

    private val shapes = listOf(
        Shape("backup-v1.0.2.json", "1.0.2", "model-1.0.2", 1) { snap ->
            assertEquals("ghp_PLAIN_102", snap.githubToken)
            assertEquals("legacy", snap.mcpName)
            assertEquals("https://example.com/mcp", snap.mcpUrl)
            assertTrue(snap.mcpServers.isEmpty())
            assertFalse(snap.inboxShowHidden)
            assertEquals("From 1.0.2", snap.chats["c102"]?.title)
            assertTrue(snap.chats["c102"]?.favorite == true)
            assertFalse(snap.chats["c102"]?.hidden == true)
            assertEquals("hello from 1.0.2", snap.conversations["c102"]?.single()?.text)
            assertEquals("draft-1.0.2", snap.drafts["c102"]?.text)
            assertEquals(-14326805, snap.themeColor)
            assertFalse(snap.notifyOnComplete)
        },
        Shape("backup-v1.0.3.json", "1.0.3–1.0.12", "model-1.0.3", 1) { snap ->
            assertTrue(snap.inboxShowHidden)
            assertTrue(snap.chats["c103"]?.hidden == true)
            assertEquals("hello from 1.0.3", snap.conversations["c103"]?.single()?.text)
        },
        Shape("backup-v1.0.13.json", "1.0.13–1.0.15", "model-1.0.13", 1) { snap ->
            assertEquals("docs", snap.mcpServers.single().name)
            assertEquals("https://example.com/mcp", snap.mcpServers.single().url)
            assertEquals("1", snap.mcpServers.single().headers["X-Test"])
            assertNull(snap.mcpServers.single().auth)
        },
        Shape("backup-v1.0.16.json", "1.0.16", "model-1.0.16", 1) { snap ->
            assertTrue(snap.chats["c116"]?.muted == true)
            assertEquals("docs", snap.mcpServers.single().name)
        },
        Shape("backup-v1.0.17.json", "1.0.17–1.0.23", "model-1.0.17", 1) { snap ->
            assertEquals("From 1.0.17", snap.chats["c117"]?.title)
            assertTrue(snap.chats["c117"]?.muted == true)
            assertEquals("draft-1.0.17", snap.drafts["c117"]?.text)
        },
        Shape("backup-v1.0.24.json", "1.0.24–1.0.25", "model-1.0.24", 1) { snap ->
            assertEquals(true, snap.drafts["c124"]?.workOnBranch)
            assertEquals("https://github.com/acme/app/pull/4", snap.drafts["c124"]?.prUrl)
            assertEquals("From 1.0.24", snap.chats["c124"]?.title)
        },
        Shape("backup-v1.0.26.json", "1.0.26–1.0.27", "model-1.0.26", 1) { snap ->
            assertTrue(snap.showThinking)
            assertTrue(snap.hideThinking)
            assertTrue(snap.showToolCalls)
            assertTrue(snap.hideTools)
            assertEquals("From 1.0.26", snap.chats["c126"]?.title)
        },
        Shape("backup-v1.0.28.json", "1.0.28", "model-1.0.28", 2) { snap ->
            assertEquals("Nexus", snap.repoGroupPrefs.styles["acme/app"]?.name)
            assertTrue(snap.repoGroupPrefs.styles["acme/app"]?.favorite == true)
            assertEquals(-14326805, snap.repoGroupPrefs.styles["acme/app"]?.color)
            assertEquals(listOf("acme/app"), snap.repoGroupPrefs.order)
            assertEquals(14, snap.machinePrefs.autoHideDays)
            assertEquals("build", snap.machinePrefs.seen["name:build"]?.name)
            assertEquals("serif", snap.uiFont)
            assertEquals("jetbrains_mono", snap.codeFont)
            assertEquals(120, snap.textScalePct)
            assertEquals("compact", snap.chatDensity)
            assertFalse(snap.groupByRepo)
            assertFalse(snap.compactCards)
            assertEquals(3, snap.hideFinishedDays)
            assertTrue(snap.chats["c128"]?.hidden == true)
            assertTrue(snap.chats["c128"]?.pinned == true)
            assertEquals("opus", snap.repoDefaults.single().modelId)
            assertEquals("f28", snap.forges.single().id)
            assertEquals("", snap.forges.single().token)
            assertNull(snap.conversations["c128"]?.single()?.atMs)
        },
        Shape("backup-v1.0.29.json", "1.0.29–1.0.31", "model-1.0.29", 2) { snap ->
            assertEquals(42L, snap.conversations["c129"]?.single()?.atMs)
            assertEquals("hello from 1.0.29", snap.conversations["c129"]?.single()?.text)
            assertEquals("cid", snap.mcpServers.single().auth?.clientId)
            assertEquals("", snap.mcpServers.single().auth?.clientSecret)
            assertTrue(snap.chats["c129"]?.pinned == true)
        },
    )
}
