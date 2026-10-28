package com.cursorandroid.app.data.repo

import android.content.Context
import android.net.Uri
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.notify.DEFAULT_MAX_LIVE_PROMOTED
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Settings export. Files from every shipped app since 1.0.2 still parse: missing fields use
 * their defaults, and unknown fields are ignored. The sign-in key is dropped on read and is never
 * written. Forge tokens, the legacy GitHub token, and MCP secrets stay out of the plain file
 * unless the user sets a passphrase, which seals them. A backup never includes pending archives.
 *
 * Shipped shapes, all with `"version": 1` until 1.0.28 started writing `"version": 2`:
 * 1.0.2; 1.0.3–1.0.12 (hidden inbox, auto-update); 1.0.13–1.0.15 (MCP list);
 * 1.0.16 (install-count flag); 1.0.17–1.0.23 (skipped update); 1.0.24–1.0.25 (no install-count);
 * 1.0.26–1.0.27 (no auto-update); 1.0.28 (forges, machines, appearance, layout, sealed secrets);
 * 1.0.29–1.0.31 (transcript time, MCP auth). 1.0.30 was not a separate build.
 * 1.0.32 adds folders and collapsed repo groups. A file that omits those keys does not clear
 * folders or collapsed groups already on the phone.
 * autoUpdate, shareInstallCount, and skippedUpdateCode are ignored. Those switches are gone.
 */
object SettingsBackup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun export(context: Context, container: AppContainer, uri: Uri, passphrase: CharArray? = null) {
        val body = json.encodeToString(buildBackup(snapshotOf(container), passphrase))
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(body.toByteArray(Charsets.UTF_8))
        } ?: error("Could not write export")
    }

    /** Reads and parses a file. A non-null result with [SettingsSnapshot.secrets] set needs a passphrase. */
    fun load(context: Context, uri: Uri): SettingsSnapshot? {
        val text = context.contentResolver.openInputStream(uri)?.use { inStream ->
            SafeLinks.readBounded(inStream, MAX_IMPORT_BYTES)?.toString(Charsets.UTF_8)
        } ?: return null
        return parse(text)
    }

    /**
     * One decoder for every shipped file, from 1.0.2 through the current export.
     * A missing field takes the default on [SettingsSnapshot] or the nested record.
     * An unknown or extra field is skipped and does not reject the file or drop the fields we know.
     * A null where a default exists is coerced to that default.
     * Files from older builds may carry the Cursor sign-in key. It is dropped here so it can never be applied or staged.
     */
    fun parse(text: String): SettingsSnapshot? =
        runCatching { json.decodeFromString<SettingsSnapshot>(text) }.getOrNull()?.copy(apiKey = null)

    fun encode(snap: SettingsSnapshot): String = json.encodeToString(snap)

    /** Applies a loaded file. A wrong passphrase applies nothing; no passphrase applies everything but the secrets. */
    fun apply(container: AppContainer, loaded: SettingsSnapshot, passphrase: CharArray?): BackupOpen {
        val opened = openBackup(loaded, passphrase)
        if (opened.result != BackupOpen.Ok) return opened.result
        applySnapshot(container, opened.snapshot)
        return BackupOpen.Ok
    }

    /** Settings > Import applies now; the sign-in screen only stages the file until sign-in completes. */
    fun restoreOrStage(container: AppContainer, loaded: SettingsSnapshot, passphrase: CharArray?, stage: Boolean): BackupOpen {
        if (!stage) return apply(container, loaded, passphrase)
        val opened = openBackup(loaded, passphrase)
        if (opened.result == BackupOpen.Ok) container.stagedRestore.stage(opened.snapshot)
        return opened.result
    }

    /** Applies the backup staged on the sign-in screen. Call right after sign-in: it never touches the session key. */
    fun applyStaged(container: AppContainer): Boolean {
        val staged = container.stagedRestore.take() ?: return false
        applySnapshot(container, staged.copy(apiKey = null, secrets = null))
        return true
    }

    internal fun capture(container: AppContainer): SettingsSnapshot = snapshotOf(container)

    private fun snapshotOf(container: AppContainer) = SettingsSnapshot(
        version = BACKUP_VERSION,
        notifyOnComplete = container.store.notifyOnComplete,
        notifyOnApproval = container.store.notifyOnApproval,
        liveRunStatus = container.store.liveRunStatus,
        maxLivePromoted = container.store.maxLivePromoted,
        mcpServers = container.store.storedMcps(),
        showThinking = container.store.showThinking,
        showToolCalls = container.store.showToolCalls,
        defaultModel = container.store.defaultModel,
        showMicrophone = container.store.showMicrophone,
        forges = container.store.forges(),
        repoDefaults = container.store.repoDefaults(),
        repoGroupPrefs = container.chats.repoGroupPrefs,
        machinePrefs = container.machines.prefs(),
        inboxWorkingOnly = container.chats.inboxWorkingOnly,
        inboxShowArchived = container.chats.inboxShowArchived,
        inboxShowHidden = container.chats.inboxShowHidden,
        themeColor = container.store.themeColor,
        uiFont = container.store.uiFont,
        codeFont = container.store.codeFont,
        textScalePct = container.store.textScalePct,
        chatDensity = container.store.chatDensity,
        homeDensity = container.store.homeDensity,
        importedFonts = container.importedFonts.exportForBackup(),
        folderIcons = container.folderIcons.exportForBackup(container.folders.snapshot()),
        showInboxEnvs = container.store.showInboxEnvs,
        showInboxRemote = container.store.showInboxRemote,
        groupByRepo = container.chats.groupByRepo,
        compactCards = container.chats.compactCards,
        hideFinishedDays = container.chats.hideFinishedDays,
        collapsedRepos = container.chats.collapsedRepos,
        folders = container.folders.snapshot(),
        chats = container.chats.snapshot(),
        conversations = container.conversations.exportAll(),
        drafts = container.drafts.exportAll(),
    )

    private fun applySnapshot(container: AppContainer, snap: SettingsSnapshot) {
        container.store.notifyOnComplete = snap.notifyOnComplete
        container.store.notifyOnApproval = snap.notifyOnApproval
        container.store.liveRunStatus = snap.liveRunStatus
        container.store.maxLivePromoted = snap.maxLivePromoted
        if (snap.mcpServers.isNotEmpty()) {
            container.store.saveStoredMcps(mergeMcpSecrets(container.store.storedMcps(), snap.mcpServers))
        } else if (snap.mcpName.isNotBlank() || snap.mcpUrl.isNotBlank()) {
            migrateLegacyMcp(snap.mcpName, snap.mcpUrl)?.let { legacy ->
                val saved = container.store.storedMcps()
                val merged = mergeMcpImport(saved, listOf(legacy), setOf(mcpNameKey(legacy.name)))
                container.store.saveStoredMcps(merged.items)
            }
        }
        container.store.showThinking = snap.showThinking && !snap.hideThinking
        container.store.showToolCalls = snap.showToolCalls && !snap.hideTools
        container.store.defaultModel = snap.defaultModel
        container.store.showMicrophone = snap.showMicrophone
        if (!snap.githubToken.isNullOrBlank()) {
            container.store.githubToken = snap.githubToken
        }
        if (snap.forges.isNotEmpty()) {
            container.store.saveForges(mergeForgeSecrets(container.store.forges(), snap.forges))
            container.forgeSync.requestDue(FORGE_SYNC_START_MS)
        }
        if (snap.repoDefaults.isNotEmpty()) {
            container.store.saveRepoDefaults(snap.repoDefaults)
        }
        if (!snap.repoGroupPrefs.isEmpty) {
            container.chats.repoGroupPrefs = snap.repoGroupPrefs
        }
        if (!snap.machinePrefs.isEmpty) {
            container.machines.replace(mergeMachinePrefs(container.machines.prefs(), snap.machinePrefs))
        }
        container.chats.inboxWorkingOnly = snap.inboxWorkingOnly
        container.chats.inboxShowArchived = snap.inboxShowArchived
        container.chats.inboxShowHidden = snap.inboxShowHidden
        container.store.themeColor = snap.themeColor
        container.store.uiFont = snap.uiFont
        container.store.codeFont = snap.codeFont
        container.store.textScalePct = snap.textScalePct
        container.store.chatDensity = snap.chatDensity
        container.store.homeDensity = snap.homeDensity
        container.importedFonts.restoreFromBackup(snap.importedFonts)
        container.store.showInboxEnvs = snap.showInboxEnvs
        container.store.showInboxRemote = snap.showInboxRemote
        container.chats.groupByRepo = snap.groupByRepo
        container.chats.compactCards = snap.compactCards
        container.chats.hideFinishedDays = snap.hideFinishedDays
        snap.collapsedRepos?.let { container.chats.collapsedRepos = it }
        snap.folders?.let { container.folders.replace(it) }
        container.folderIcons.restoreFromBackup(snap.folderIcons)
        container.chats.mergeAll(snap.chats)
        if (snap.conversations.isNotEmpty()) {
            container.conversations.importAll(snap.conversations)
        }
        if (snap.drafts.isNotEmpty()) {
            container.drafts.importAll(snap.drafts)
        }
    }

    private const val MAX_IMPORT_BYTES = 8L * 1024L * 1024L
}

@Serializable
data class SettingsSnapshot(
    val version: Int = 1,
    // Read only, for files from before the sign-in key was dropped from backups. Never written, never applied.
    val apiKey: String? = null,
    val notifyOnComplete: Boolean = true,
    val notifyOnApproval: Boolean = true,
    val liveRunStatus: Boolean = false,
    val maxLivePromoted: Int = DEFAULT_MAX_LIVE_PROMOTED,
    // Read only, for files from before the MCP list. Never written.
    val mcpName: String = "",
    val mcpUrl: String = "",
    val mcpServers: List<StoredMcpServer> = emptyList(),
    val showThinking: Boolean = true,
    val showToolCalls: Boolean = true,
    val hideThinking: Boolean = false,
    val hideTools: Boolean = false,
    val defaultModel: String = "",
    val showMicrophone: Boolean = true,
    val githubToken: String? = null,
    val forges: List<ForgeConnection> = emptyList(),
    val repoDefaults: List<RepoDefault> = emptyList(),
    val repoGroupPrefs: RepoGroupPrefs = RepoGroupPrefs(),
    val machinePrefs: MachinePrefs = MachinePrefs(),
    val inboxWorkingOnly: Boolean = false,
    val inboxShowArchived: Boolean = false,
    val inboxShowHidden: Boolean = false,
    val themeColor: Int = 0xFFF54E00.toInt(),
    val uiFont: String = "system",
    val codeFont: String = "system_mono",
    val textScalePct: Int = 100,
    val chatDensity: String = "comfortable",
    val homeDensity: String = "comfortable",
    val importedFonts: List<ImportedFontBackup> = emptyList(),
    val folderIcons: List<FolderIconBackup> = emptyList(),
    val showInboxEnvs: Boolean = true,
    val showInboxRemote: Boolean = true,
    val groupByRepo: Boolean = true,
    val compactCards: Boolean = true,
    val hideFinishedDays: Int = 0,
    /**
     * Null when the file is from before 1.0.32. Applying a null leaves the groups the phone
     * already has collapsed. An empty set means the user had none collapsed.
     */
    val collapsedRepos: Set<String>? = null,
    /**
     * Null when the file is from before 1.0.32. Applying a null leaves folders already on the phone.
     * Pending archives are never stored here, so a restore cannot send one.
     */
    val folders: FolderMembership? = null,
    val chats: Map<String, ChatMeta> = emptyMap(),
    val conversations: Map<String, List<TranscriptLine>> = emptyMap(),
    val drafts: Map<String, ChatDraft> = emptyMap(),
    val secrets: SealedSecrets? = null,
)
