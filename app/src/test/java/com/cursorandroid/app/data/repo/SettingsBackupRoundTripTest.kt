package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.ui.inbox.folderIconChoices
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsBackupRoundTripTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = AppContainer(context)
        container.store.demoMode = false
        container.store.apiKey = "key_CURRENT_SESSION"
    }

    @Test
    fun pickerOffersEveryStoredIcon() {
        val offered = folderIconChoices().map { it.first }.filter { it.isNotEmpty() }
        assertEquals(LocalFolderStore.ICON_CHOICES, offered)
    }

    @Test
    fun exportWipeImportRestoresEverySavedFieldExceptSecrets() {
        wipe()
        seed()
        PendingArchiveStore(container.ui).save(
            listOf(PendingArchive(agentId = "keep-pending", previousStatus = "FINISHED", requestedAt = 1, dueAt = 9)),
        )
        val exported = buildBackup(SettingsBackup.capture(container), passphrase = null)
        val text = SettingsBackup.encode(exported)
        assertFalse(text.contains("key_CURRENT_SESSION"))
        assertFalse(text.contains("ghp_SECRET_TOKEN"))
        assertFalse(text.contains("mcp-secret-value"))
        assertFalse(text.contains("oauth-secret"))
        assertFalse(text.contains("pendingArchives"))
        assertFalse(text.contains("keep-pending"))
        assertTrue(text.contains("\"folders\""))
        assertTrue(text.contains("\"collapsedRepos\""))

        wipe()
        val tampered = withPendingArchive(text, "sneaky-send")
        val loaded = checkNotNull(SettingsBackup.parse(tampered))
        assertEquals(BackupOpen.Ok, SettingsBackup.apply(container, loaded, passphrase = null))

        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals(exported, SettingsBackup.capture(container))
        assertEquals(listOf("keep-pending"), PendingArchiveStore(container.ui).load().map { it.agentId })
        assertEquals("", container.store.forges().single().token)
        assertEquals("", container.store.storedMcps().single().headers["Authorization"])
        assertEquals("", container.store.storedMcps().single().auth?.clientSecret)
    }

    @Test
    fun backupsFromEveryShippedShapeApply() {
        for (name in listOf(
            "backup-v1.0.2.json",
            "backup-v1.0.3.json",
            "backup-v1.0.13.json",
            "backup-v1.0.16.json",
            "backup-v1.0.17.json",
            "backup-v1.0.24.json",
            "backup-v1.0.26.json",
            "backup-v1.0.28.json",
            "backup-v1.0.29.json",
        )) {
            val loaded = checkNotNull(SettingsBackup.parse(read(name)))
            assertEquals(name, BackupOpen.Ok, SettingsBackup.apply(container, loaded, passphrase = null))
            assertEquals(name, loaded.defaultModel, container.store.defaultModel)
            assertEquals(name, "key_CURRENT_SESSION", container.store.apiKey)
        }
    }

    @Test
    fun anOldBackupKeepsFoldersCollapsedGroupsAndPendingArchives() {
        container.folders.replace(
            FolderMembership(
                folders = listOf(LocalFolder(id = "fld-stay", name = "Stay", createdAt = 1, color = LocalFolderStore.NAME_COLORS[2], icon = "work")),
            ),
        )
        container.chats.collapsedRepos = setOf("acme/stay")
        PendingArchiveStore(container.ui).save(
            listOf(PendingArchive(agentId = "keep-me", previousStatus = "FINISHED", requestedAt = 1, dueAt = 2)),
        )

        applyFile("backup-v1.0.2.json")

        assertEquals("model-1.0.2", container.store.defaultModel)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals("ghp_PLAIN_102", container.store.githubToken)
        assertEquals("legacy", container.store.storedMcps().single().name)
        assertEquals("From 1.0.2", container.chats.meta("c102").title)
        assertTrue(container.chats.meta("c102").favorite)
        assertEquals("hello from 1.0.2", container.conversations.load("c102").single().text)
        assertEquals("draft-1.0.2", container.drafts.load("c102").text)
        assertEquals(0xFF2563EB.toInt(), container.store.themeColor)
        assertFalse(container.store.notifyOnComplete)
        assertTrue(container.chats.inboxWorkingOnly)
        assertEquals("Stay", container.folders.snapshot().folders.single().name)
        assertEquals(setOf("acme/stay"), container.chats.collapsedRepos)
        assertEquals(listOf("keep-me"), PendingArchiveStore(container.ui).load().map { it.agentId })
    }

    @Test
    fun aVersionTwoBackupWithoutFoldersDoesNotWipeThem() {
        container.folders.replace(
            FolderMembership(folders = listOf(LocalFolder(id = "fld-stay", name = "Stay", icon = "code"))),
        )
        container.chats.collapsedRepos = setOf("acme/stay")
        container.chats.groupByRepo = true
        container.chats.compactCards = true
        PendingArchiveStore(container.ui).save(
            listOf(PendingArchive(agentId = "keep-me", previousStatus = "RUNNING")),
        )

        applyFile("backup-v1.0.28.json")

        assertEquals("model-1.0.28", container.store.defaultModel)
        assertEquals("Nexus", container.chats.repoGroupPrefs.styles["acme/app"]?.name)
        assertTrue(container.chats.repoGroupPrefs.styles["acme/app"]?.favorite == true)
        assertEquals(listOf("acme/app"), container.chats.repoGroupPrefs.order)
        assertEquals(14, container.machines.prefs().autoHideDays)
        assertEquals("build", container.machines.prefs().seen["name:build"]?.name)
        assertEquals("serif", container.store.uiFont)
        assertEquals("jetbrains_mono", container.store.codeFont)
        assertEquals(120, container.store.textScalePct)
        assertEquals("compact", container.store.chatDensity)
        assertFalse(container.chats.groupByRepo)
        assertFalse(container.chats.compactCards)
        assertEquals(3, container.chats.hideFinishedDays)
        assertTrue(container.chats.meta("c128").hidden)
        assertTrue(container.chats.meta("c128").pinned)
        assertEquals("opus", container.store.repoDefaults().single().modelId)
        assertEquals("Stay", container.folders.snapshot().folders.single().name)
        assertEquals("code", container.folders.snapshot().folders.single().icon)
        assertEquals(setOf("acme/stay"), container.chats.collapsedRepos)
        assertEquals(listOf("keep-me"), PendingArchiveStore(container.ui).load().map { it.agentId })
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
    }

    @Test
    fun legacyHideFlagsTurnTheSwitchesOff() {
        container.store.showThinking = true
        container.store.showToolCalls = true
        applyFile("backup-v1.0.26.json")
        assertFalse(container.store.showThinking)
        assertFalse(container.store.showToolCalls)
        assertEquals("From 1.0.26", container.chats.meta("c126").title)
    }

    @Test
    fun transcriptTimeAndMcpAuthFrom1029Apply() {
        applyFile("backup-v1.0.29.json")
        assertEquals(42L, container.conversations.load("c129").single().atMs)
        assertEquals("cid", container.store.storedMcps().single().auth?.clientId)
        assertTrue(container.chats.meta("c129").pinned)
    }

    private fun applyFile(name: String) {
        val loaded = checkNotNull(SettingsBackup.parse(read(name)))
        assertEquals(BackupOpen.Ok, SettingsBackup.apply(container, loaded, passphrase = null))
    }

    private fun read(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!.bufferedReader().use { it.readText() }

    private fun seed() {
        container.store.notifyOnComplete = false
        container.store.notifyOnApproval = false
        container.store.saveStoredMcps(
            listOf(
                StoredMcpServer(
                    id = "m1",
                    name = "Docs",
                    url = "https://example.com/mcp",
                    headers = mapOf("Authorization" to "Bearer mcp-secret-value"),
                    auth = StoredMcpAuth(clientId = "cid", clientSecret = "oauth-secret", scopes = listOf("read")),
                ),
            ),
        )
        container.store.showThinking = false
        container.store.showToolCalls = false
        container.store.defaultModel = "composer-2"
        container.store.showMicrophone = false
        container.store.saveForges(
            listOf(ForgeConnection(id = "f1", provider = ForgeKind.GITHUB.id, name = "GitHub", token = "ghp_SECRET_TOKEN")),
        )
        container.store.saveRepoDefaults(
            listOf(
                RepoDefault(
                    repoUrl = "https://github.com/acme/app",
                    modelId = "opus",
                    modelParams = listOf(ModelParam("effort", "high")),
                    branch = "dev",
                    openFinishedPr = true,
                ),
            ),
        )
        container.chats.repoGroupPrefs = RepoGroupPrefs(
            styles = mapOf("acme/app" to RepoGroupStyle(name = "Nexus", favorite = true, color = 0xFF2563EB.toInt())),
            order = listOf("acme/app", "acme/other"),
        )
        container.machines.replace(
            MachinePrefs(
                seen = mapOf("name:build" to MachineRecord(name = "build", lastSeenMs = 5, firstSeenMs = 1)),
                marks = mapOf("name:build" to MachineMark(state = MachineMarkState.SHOWN, atMs = 6, name = "build")),
                autoHideDays = 14,
            ),
        )
        container.chats.inboxWorkingOnly = true
        container.chats.inboxShowArchived = true
        container.chats.inboxShowHidden = true
        container.store.themeColor = 0xFF2563EB.toInt()
        container.store.uiFont = "serif"
        container.store.codeFont = "jetbrains_mono"
        container.store.textScalePct = 120
        container.store.chatDensity = "compact"
        container.store.showInboxEnvs = false
        container.store.showInboxRemote = false
        container.chats.groupByRepo = false
        container.chats.compactCards = false
        container.chats.hideFinishedDays = 7
        container.chats.collapsedRepos = setOf("acme/other")
        container.folders.replace(
            FolderMembership(
                folders = listOf(
                    LocalFolder(
                        id = "fld-1",
                        name = "Work",
                        createdAt = 10,
                        collapsed = true,
                        favorite = true,
                        color = LocalFolderStore.NAME_COLORS[5],
                        icon = "code",
                    ),
                ),
                chats = mapOf("chat-1" to "fld-1"),
                groups = mapOf("acme/app" to "fld-1"),
            ),
        )
        container.chats.replaceAll(
            mapOf(
                "chat-1" to ChatMeta(
                    title = "Nick",
                    favorite = true,
                    favoritedAt = 3,
                    hidden = true,
                    muted = true,
                    repoUrl = "https://github.com/acme/app",
                    baseBranch = "main",
                    startSha = "aaa",
                    ignoredRemoteSha = "bbb",
                    pinned = true,
                    pinnedAt = 4,
                    openFinishedPr = true,
                ),
            ),
        )
        container.conversations.importAll(
            mapOf("chat-1" to listOf(TranscriptLine(id = "l1", kind = "user", text = "hello", atMs = 99))),
        )
        container.drafts.save(
            "chat-1",
            ChatDraft(text = "keep this draft", mode = "agent", modelId = "opus", prUrl = "https://github.com/acme/app/pull/1", workOnBranch = true),
        )
    }

    private fun wipe() {
        container.store.notifyOnComplete = true
        container.store.notifyOnApproval = true
        container.store.saveStoredMcps(emptyList())
        container.store.showThinking = true
        container.store.showToolCalls = true
        container.store.defaultModel = ""
        container.store.showMicrophone = true
        container.store.saveForges(emptyList())
        container.store.saveRepoDefaults(emptyList())
        container.chats.repoGroupPrefs = RepoGroupPrefs()
        container.machines.replace(MachinePrefs())
        container.chats.inboxWorkingOnly = false
        container.chats.inboxShowArchived = false
        container.chats.inboxShowHidden = false
        container.store.themeColor = 0xFFF54E00.toInt()
        container.store.uiFont = "system"
        container.store.codeFont = "system_mono"
        container.store.chatDensity = "comfortable"
        container.store.textScalePct = 100
        container.store.showInboxEnvs = true
        container.store.showInboxRemote = true
        container.chats.groupByRepo = true
        container.chats.compactCards = true
        container.chats.hideFinishedDays = 0
        container.chats.collapsedRepos = emptySet()
        container.folders.replace(FolderMembership())
        container.chats.replaceAll(emptyMap())
        container.conversations.exportAll().keys.forEach { container.conversations.remove(it) }
        container.drafts.exportAll().keys.forEach { container.drafts.clear(it) }
    }

    private fun withPendingArchive(text: String, agentId: String): String {
        val extra = buildJsonArray {
            add(buildJsonObject { put("agentId", JsonPrimitive(agentId)) })
        }
        val obj = Json.parseToJsonElement(text).jsonObject
        return JsonObject(obj + ("pendingArchives" to extra)).toString()
    }
}
