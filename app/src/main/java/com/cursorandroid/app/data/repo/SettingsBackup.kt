package com.cursorandroid.app.data.repo

import android.content.Context
import android.net.Uri
import com.cursorandroid.app.AppContainer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SettingsBackup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(context: Context, container: AppContainer, uri: Uri) {
        val snap = SettingsSnapshot(
            apiKey = container.store.apiKey,
            notifyOnComplete = container.store.notifyOnComplete,
            notifyOnApproval = container.store.notifyOnApproval,
            mcpName = container.store.mcpName,
            mcpUrl = container.store.mcpUrl,
            mcpServers = container.store.storedMcps(),
            showThinking = container.store.showThinking,
            showToolCalls = container.store.showToolCalls,
            defaultModel = container.store.defaultModel,
            showMicrophone = container.store.showMicrophone,
            githubToken = container.store.githubToken,
            forges = container.store.forges(),
            repoDefaults = container.store.repoDefaults(),
            inboxWorkingOnly = container.chats.inboxWorkingOnly,
            inboxShowArchived = container.chats.inboxShowArchived,
            inboxShowHidden = container.chats.inboxShowHidden,
            themeColor = container.store.themeColor,
            uiFont = container.store.uiFont,
            codeFont = container.store.codeFont,
            textScalePct = container.store.textScalePct,
            chatDensity = container.store.chatDensity,
            showInboxEnvs = container.store.showInboxEnvs,
            showInboxRemote = container.store.showInboxRemote,
            groupByRepo = container.chats.groupByRepo,
            compactCards = container.chats.compactCards,
            hideFinishedDays = container.chats.hideFinishedDays,
            chats = container.chats.snapshot(),
            conversations = container.conversations.exportAll(),
            drafts = container.drafts.exportAll(),
        )
        val body = json.encodeToString(snap)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(body.toByteArray(Charsets.UTF_8))
        } ?: error("Could not write export")
    }

    fun import(context: Context, container: AppContainer, uri: Uri): Boolean {
        val text = context.contentResolver.openInputStream(uri)?.use { inStream ->
            SafeLinks.readBounded(inStream, MAX_IMPORT_BYTES)?.toString(Charsets.UTF_8)
        } ?: return false
        val snap = runCatching { json.decodeFromString<SettingsSnapshot>(text) }.getOrNull()
            ?: return false
        if (!snap.apiKey.isNullOrBlank()) {
            container.store.apiKey = snap.apiKey
        }
        container.store.notifyOnComplete = snap.notifyOnComplete
        container.store.notifyOnApproval = snap.notifyOnApproval
        if (snap.mcpServers.isNotEmpty()) {
            container.store.saveStoredMcps(snap.mcpServers)
        } else if (snap.mcpName.isNotBlank() || snap.mcpUrl.isNotBlank()) {
            container.store.mcpName = snap.mcpName
            container.store.mcpUrl = snap.mcpUrl
        }
        container.store.showThinking = snap.showThinking && !snap.hideThinking
        container.store.showToolCalls = snap.showToolCalls && !snap.hideTools
        container.store.defaultModel = snap.defaultModel
        container.store.showMicrophone = snap.showMicrophone
        if (!snap.githubToken.isNullOrBlank()) {
            container.store.githubToken = snap.githubToken
        }
        if (snap.forges.isNotEmpty()) {
            container.store.saveForges(snap.forges)
        }
        if (snap.repoDefaults.isNotEmpty()) {
            container.store.saveRepoDefaults(snap.repoDefaults)
        }
        container.chats.inboxWorkingOnly = snap.inboxWorkingOnly
        container.chats.inboxShowArchived = snap.inboxShowArchived
        container.chats.inboxShowHidden = snap.inboxShowHidden
        container.store.themeColor = snap.themeColor
        container.store.uiFont = snap.uiFont
        container.store.codeFont = snap.codeFont
        container.store.textScalePct = snap.textScalePct
        container.store.chatDensity = snap.chatDensity
        container.store.showInboxEnvs = snap.showInboxEnvs
        container.store.showInboxRemote = snap.showInboxRemote
        container.chats.groupByRepo = snap.groupByRepo
        container.chats.compactCards = snap.compactCards
        container.chats.hideFinishedDays = snap.hideFinishedDays
        container.chats.mergeAll(snap.chats)
        if (snap.conversations.isNotEmpty()) {
            container.conversations.importAll(snap.conversations)
        }
        if (snap.drafts.isNotEmpty()) {
            container.drafts.importAll(snap.drafts)
        }
        return true
    }

    private const val MAX_IMPORT_BYTES = 8L * 1024L * 1024L
}

@Serializable
data class SettingsSnapshot(
    val version: Int = 1,
    val apiKey: String? = null,
    val notifyOnComplete: Boolean = true,
    val notifyOnApproval: Boolean = true,
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
    val inboxWorkingOnly: Boolean = false,
    val inboxShowArchived: Boolean = false,
    val inboxShowHidden: Boolean = false,
    val themeColor: Int = 0xFFF54E00.toInt(),
    val uiFont: String = "system",
    val codeFont: String = "system_mono",
    val textScalePct: Int = 100,
    val chatDensity: String = "comfortable",
    val showInboxEnvs: Boolean = true,
    val showInboxRemote: Boolean = true,
    val groupByRepo: Boolean = true,
    val compactCards: Boolean = true,
    val hideFinishedDays: Int = 0,
    val chats: Map<String, ChatMeta> = emptyMap(),
    val conversations: Map<String, List<TranscriptLine>> = emptyMap(),
    val drafts: Map<String, ChatDraft> = emptyMap(),
)
