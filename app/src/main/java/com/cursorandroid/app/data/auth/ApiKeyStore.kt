package com.cursorandroid.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import com.cursorandroid.app.data.api.McpServer
import com.cursorandroid.app.data.repo.ForgeConnection
import com.cursorandroid.app.data.repo.ForgeKind
import com.cursorandroid.app.data.repo.RepoDefault
import com.cursorandroid.app.data.repo.StoredMcpServer
import com.cursorandroid.app.data.repo.decodeForges
import com.cursorandroid.app.data.repo.decodeRepoDefaults
import com.cursorandroid.app.data.repo.decodeStoredMcps
import com.cursorandroid.app.data.repo.encodeForges
import com.cursorandroid.app.data.repo.encodeRepoDefaults
import com.cursorandroid.app.data.repo.encodeStoredMcps
import com.cursorandroid.app.data.repo.findRepoDefault
import com.cursorandroid.app.data.repo.forgeForLabel
import com.cursorandroid.app.data.repo.forgeForRepo
import com.cursorandroid.app.data.repo.migrateForges
import com.cursorandroid.app.data.repo.migrateLegacyMcp
import com.cursorandroid.app.data.repo.storedMcpsToApi
import com.cursorandroid.app.data.repo.upsertPublicGithub
import com.cursorandroid.app.data.repo.UiKeys
import com.cursorandroid.app.data.repo.UiPrefsStore
import com.cursorandroid.app.data.repo.upsertRepoDefault

class ApiKeyStore internal constructor(
    context: Context,
    private val ui: UiPrefsStore,
    secureOpener: (Context, String) -> SharedPreferences?,
) {
    constructor(context: Context, ui: UiPrefsStore) : this(context, ui, ::openEncrypted)


    private val app = context.applicationContext
    private val primary = secureOpener(app, PREFS)
    private val backup = secureOpener(app, PREFS_BAK)
    // Survives uninstall/downgrade when Android keeps app data or restores backup.
    // Encrypted prefs cannot: Keystore keys die with the package.
    private val fallback = app.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
    private val notifyPrefs = app.getSharedPreferences(PREFS_NOTIFY, Context.MODE_PRIVATE)

    init {
        recover()
        runCatching { migrateSecrets() }
        runCatching { forges() }
    }

    var apiKey: String?
        get() = readKey()
        set(value) {
            writeKey(value?.trim()?.takeIf { it.isNotEmpty() })
        }

    var notifyOnComplete: Boolean
        get() = notifyPrefs.getBoolean(NOTIFY, true)
        set(value) {
            notifyPrefs.edit { putBoolean(NOTIFY, value) }
        }

    var notifyOnApproval: Boolean
        get() = notifyPrefs.getBoolean(NOTIFY_APPROVAL, true)
        set(value) {
            notifyPrefs.edit { putBoolean(NOTIFY_APPROVAL, value) }
        }

    var showToolCalls: Boolean
        get() = notifyPrefs.getBoolean(SHOW_TOOLS, true)
        set(value) {
            notifyPrefs.edit { putBoolean(SHOW_TOOLS, value) }
        }

    var showThinking: Boolean
        get() = notifyPrefs.getBoolean(SHOW_THINKING, true)
        set(value) {
            notifyPrefs.edit { putBoolean(SHOW_THINKING, value) }
        }

    var mcpName: String
        get() = storedMcps().firstOrNull { !it.isStdio() }?.name.orEmpty()
        set(value) {
            upsertLegacyHttp(name = value, url = mcpUrl)
        }

    var mcpUrl: String
        get() = storedMcps().firstOrNull { !it.isStdio() }?.url.orEmpty()
        set(value) {
            upsertLegacyHttp(name = mcpName, url = value)
        }

    var defaultModel: String
        get() = notifyPrefs.getString(DEFAULT_MODEL, "").orEmpty()
        set(value) {
            notifyPrefs.edit { putString(DEFAULT_MODEL, value.trim()) }
        }

    var showMicrophone: Boolean
        get() = notifyPrefs.getBoolean(SHOW_MIC, true)
        set(value) {
            notifyPrefs.edit { putBoolean(SHOW_MIC, value) }
        }

    var themeColor: Int
        get() {
            val stored = ui[UiKeys.themeColor] ?: DEFAULT_THEME_COLOR
            return if ((stored ushr 24) == 0) DEFAULT_THEME_COLOR else stored
        }
        set(value) {
            val packed = if ((value ushr 24) == 0) DEFAULT_THEME_COLOR else value
            ui.put(UiKeys.themeColor, packed)
        }

    var uiFont: String
        get() = ui[UiKeys.uiFont] ?: "system"
        set(value) = ui.put(UiKeys.uiFont, value)

    var codeFont: String
        get() = ui[UiKeys.codeFont] ?: "system_mono"
        set(value) = ui.put(UiKeys.codeFont, value)

    var textScalePct: Int
        get() = ui[UiKeys.textScalePct] ?: 100
        set(value) = ui.put(UiKeys.textScalePct, value)

    var chatDensity: String
        get() = ui[UiKeys.chatDensity] ?: "comfortable"
        set(value) = ui.put(UiKeys.chatDensity, value)

    var showInboxEnvs: Boolean
        get() = ui[UiKeys.showInboxEnvs] ?: true
        set(value) = ui.put(UiKeys.showInboxEnvs, value)

    var showInboxRemote: Boolean
        get() = ui[UiKeys.showInboxRemote] ?: true
        set(value) = ui.put(UiKeys.showInboxRemote, value)

    var batteryAsked: Boolean
        get() = notifyPrefs.getBoolean(BATTERY_ASKED, false)
        set(value) {
            notifyPrefs.edit(commit = true) { putBoolean(BATTERY_ASKED, value) }
        }

    var batteryKnownExempt: Boolean
        get() = notifyPrefs.getBoolean(BATTERY_KNOWN_EXEMPT, false)
        set(value) {
            notifyPrefs.edit(commit = true) { putBoolean(BATTERY_KNOWN_EXEMPT, value) }
        }

    var feedbackNoticeSeen: Boolean
        get() = notifyPrefs.getBoolean(FEEDBACK_NOTICE, false)
        set(value) {
            notifyPrefs.edit { putBoolean(FEEDBACK_NOTICE, value) }
        }

    var githubToken: String?
        get() = forges().firstOrNull { it.provider == com.cursorandroid.app.data.repo.ForgeKind.GITHUB.id }?.token
            ?.takeIf { it.isNotBlank() }
            ?: readSecret(GITHUB)
        set(value) {
            val token = value?.trim()?.takeIf { it.isNotEmpty() }
            val raw = readSecret(FORGES)
            val current = decodeForges(raw)
            if (current.isEmpty() && !raw.isNullOrBlank()) stashUnreadable(FORGES, raw)
            val base = if (current.isEmpty()) migrateForges(emptyList(), readSecret(GITHUB)) else current
            saveForges(upsertPublicGithub(base, token))
            if (token == null) removeLegacyGithub()
        }

    fun forges(): List<ForgeConnection> {
        val raw = readSecret(FORGES)
        val stored = decodeForges(raw)
        if (stored.isEmpty() && !raw.isNullOrBlank()) return emptyList()
        val legacy = readSecret(GITHUB)
        val migrated = migrateForges(stored, legacy)
        if (migrated != stored) saveForges(migrated)
        return migrated
    }

    fun saveForges(items: List<ForgeConnection>) {
        val next = items.take(20)
        writeSecret(FORGES, if (next.isEmpty()) null else encodeForges(next))
    }

    fun forgeForRepo(repoUrl: String): ForgeConnection? = forgeForRepo(forges(), repoUrl)

    fun forgeForLabel(label: String): ForgeConnection? = forgeForLabel(forges(), label)

    fun repoDefaults(): List<RepoDefault> = decodeRepoDefaults(readSecret(REPO_DEFAULTS))

    fun repoDefault(url: String): RepoDefault? = findRepoDefault(repoDefaults(), url)

    fun saveRepoDefaults(items: List<RepoDefault>) {
        val next = items.take(50)
        writeSecret(REPO_DEFAULTS, if (next.isEmpty()) null else encodeRepoDefaults(next))
    }

    fun saveRepoDefault(item: RepoDefault) {
        saveRepoDefaults(upsertRepoDefault(repoDefaults(), item))
    }

    fun deleteRepoDefault(url: String) {
        val key = com.cursorandroid.app.data.api.repoKey(url)
        saveRepoDefaults(repoDefaults().filterNot { com.cursorandroid.app.data.api.repoKey(it.repoUrl) == key })
    }

    fun storedMcps(): List<StoredMcpServer> {
        val stored = decodeStoredMcps(readSecret(MCP_LIST))
        if (stored.isNotEmpty()) return stored
        val legacy = migrateLegacyMcp(
            notifyPrefs.getString(MCP_NAME, "").orEmpty(),
            notifyPrefs.getString(MCP_URL, "").orEmpty(),
        ) ?: return emptyList()
        saveStoredMcps(listOf(legacy))
        return listOf(legacy)
    }

    fun saveStoredMcps(items: List<StoredMcpServer>) {
        val next = items.take(50)
        writeSecret(MCP_LIST, if (next.isEmpty()) null else encodeStoredMcps(next))
        val first = next.firstOrNull { !it.isStdio() }
        notifyPrefs.edit {
            putString(MCP_NAME, first?.name.orEmpty())
            putString(MCP_URL, first?.url.orEmpty())
        }
    }

    fun mcpServers(): List<McpServer>? = storedMcpsToApi(storedMcps())

    private fun upsertLegacyHttp(name: String, url: String) {
        val items = storedMcps().toMutableList()
        val idx = items.indexOfFirst { !it.isStdio() }
        val next = StoredMcpServer(
            id = items.getOrNull(idx)?.id ?: java.util.UUID.randomUUID().toString(),
            enabled = items.getOrNull(idx)?.enabled ?: true,
            name = name,
            type = com.cursorandroid.app.data.repo.TYPE_HTTP,
            url = url,
            headers = items.getOrNull(idx)?.headers.orEmpty(),
        )
        if (idx >= 0) items[idx] = next else items.add(next)
        saveStoredMcps(items)
    }

    var demoMode: Boolean
        get() = notifyPrefs.getBoolean(DEMO_MODE, false)
        set(value) {
            notifyPrefs.edit { putBoolean(DEMO_MODE, value) }
        }

    fun hasKey(): Boolean = !apiKey.isNullOrBlank()

    fun hasSession(): Boolean = demoMode || hasKey()

    fun clear() {
        writeSecret(KEY, null)
        removeLegacyGithub()
        writeSecret(FORGES, null)
    }

    private fun readKey(): String? = readSecret(KEY)

    private fun writeKey(value: String?) = writeSecret(KEY, value)

    private fun readSecret(name: String): String? {
        return listOf(primary, backup)
            .mapNotNull { it?.getString(name, null)?.trim()?.takeIf { key -> key.isNotEmpty() } }
            .firstOrNull()
            ?: fallback.getString(name, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun writeSecret(name: String, value: String?) {
        val stored = value.orEmpty()
        val encrypted = listOfNotNull(primary, backup)
        encrypted.forEach { prefs ->
            prefs.edit { if (value.isNullOrEmpty()) remove(name) else putString(name, stored) }
        }
        fallback.edit {
            if (encrypted.isEmpty() && !value.isNullOrEmpty()) {
                putString(name, value)
            } else {
                remove(name)
            }
        }
    }

    private fun recover() {
        for (name in listOf(KEY, FORGES, REPO_DEFAULTS, MCP_LIST, GITHUB)) {
            val found = readSecret(name)
            if (!found.isNullOrEmpty()) writeSecret(name, found)
        }
    }

    private fun legacyGithubTokens(): List<String> {
        val encrypted = listOfNotNull(primary, backup).map { it.getString(GITHUB, null) }
        return (encrypted + fallback.getString(GITHUB, null))
            .mapNotNull { it?.trim()?.takeIf { token -> token.isNotEmpty() } }
    }

    private fun removeLegacyGithub() {
        writeSecret(GITHUB, null)
    }

    /**
     * Versioned. 0 -> 1 moves the pre-forge `github_token` into the forge list. The new entry is read
     * back before any legacy copy is removed, an existing GitHub token in the forge list is never
     * replaced, and the version is recorded only when every step held, so a failed run repeats.
     */
    private fun migrateSecrets() {
        if (notifyPrefs.getInt(SECRETS_SCHEMA, 0) >= SECRETS_VERSION) return
        if (!migrateGithubToken()) return
        notifyPrefs.edit(commit = true) { putInt(SECRETS_SCHEMA, SECRETS_VERSION) }
    }

    private fun migrateGithubToken(): Boolean {
        val legacy = legacyGithubTokens().firstOrNull() ?: return true
        val raw = readSecret(FORGES)
        val stored = decodeForges(raw)
        if (stored.isEmpty() && !raw.isNullOrBlank()) return false
        if (stored.none { it.provider == ForgeKind.GITHUB.id && it.token.isNotBlank() }) {
            saveForges(migrateForges(stored, legacy))
        }
        val verified = decodeForges(readSecret(FORGES))
            .any { it.provider == ForgeKind.GITHUB.id && it.token.isNotBlank() }
        if (!verified) return false
        removeLegacyGithub()
        return true
    }

    private fun stashUnreadable(name: String, raw: String) {
        if (readSecret("$name$UNREADABLE") == null) writeSecret("$name$UNREADABLE", raw)
    }

    companion object {
        private fun openEncrypted(context: Context, name: String): SharedPreferences? {
            val opened = runCatching { createEncrypted(context, name) }.getOrNull()
            if (opened != null) return opened
            setAside(context, name)
            return runCatching { createEncrypted(context, name) }.getOrNull()
        }

        // A file that will not open may only be unreadable to this build, so keep it instead of deleting it.
        private fun setAside(context: Context, name: String) {
            val file = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
            if (!file.exists()) return
            val kept = File(file.parentFile, "$name.xml.unreadable")
            if (!kept.exists() && file.renameTo(kept)) return
            runCatching { context.deleteSharedPreferences(name) }
        }

        private fun createEncrypted(context: Context, name: String): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private const val PREFS = "cursor_secure"
        private const val PREFS_BAK = "cursor_secure_bak"
        private const val PREFS_FALLBACK = "cursor_secure_fallback"
        private const val PREFS_NOTIFY = "cursor_prefs"
        private const val KEY = "api_key"
        private const val GITHUB = "github_token"
        private const val UNREADABLE = "_unreadable"
        private const val SECRETS_SCHEMA = "secrets_schema"
        private const val SECRETS_VERSION = 1
        private const val FORGES = "forges"
        private const val REPO_DEFAULTS = "repo_defaults"
        private const val NOTIFY = "notify_on_complete"
        private const val NOTIFY_APPROVAL = "notify_on_approval"
        private const val SHOW_TOOLS = "show_tool_calls"
        private const val SHOW_THINKING = "show_thinking"
        private const val MCP_NAME = "mcp_name"
        private const val MCP_URL = "mcp_url"
        private const val MCP_LIST = "mcp_list"
        private const val DEFAULT_MODEL = "default_model"
        private const val SHOW_MIC = "show_microphone"
        private const val BATTERY_ASKED = "battery_asked"
        private const val BATTERY_KNOWN_EXEMPT = "battery_known_exempt"
        private const val FEEDBACK_NOTICE = "feedback_notice_seen"
        private const val DEMO_MODE = "demo_mode"
        const val DEFAULT_THEME_COLOR = 0xFFF54E00.toInt()
    }
}
