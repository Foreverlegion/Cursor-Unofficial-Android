package com.cursorandroid.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cursorandroid.app.data.api.McpServer
import com.cursorandroid.app.data.repo.StoredMcpServer
import com.cursorandroid.app.data.repo.decodeStoredMcps
import com.cursorandroid.app.data.repo.encodeStoredMcps
import com.cursorandroid.app.data.repo.migrateLegacyMcp
import com.cursorandroid.app.data.repo.storedMcpsToApi

class ApiKeyStore(context: Context) {
    private val app = context.applicationContext
    private val primary = openEncrypted(app, PREFS)
    private val backup = openEncrypted(app, PREFS_BAK)
    // Survives uninstall/downgrade when Android keeps app data or restores backup.
    // Encrypted prefs cannot: Keystore keys die with the package.
    private val fallback = app.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
    private val notifyPrefs = app.getSharedPreferences(PREFS_NOTIFY, Context.MODE_PRIVATE)

    init {
        recover()
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
            val stored = notifyPrefs.getInt(THEME_COLOR, DEFAULT_THEME_COLOR)
            return if ((stored ushr 24) == 0) DEFAULT_THEME_COLOR else stored
        }
        set(value) {
            val packed = if ((value ushr 24) == 0) DEFAULT_THEME_COLOR else value
            notifyPrefs.edit { putInt(THEME_COLOR, packed) }
        }

    var showInboxEnvs: Boolean
        get() = notifyPrefs.getBoolean(SHOW_INBOX_ENVS, true)
        set(value) {
            notifyPrefs.edit { putBoolean(SHOW_INBOX_ENVS, value) }
        }

    var showInboxRemote: Boolean
        get() = notifyPrefs.getBoolean(SHOW_INBOX_REMOTE, true)
        set(value) {
            notifyPrefs.edit { putBoolean(SHOW_INBOX_REMOTE, value) }
        }

    var autoUpdate: Boolean
        get() = notifyPrefs.getBoolean(AUTO_UPDATE, false)
        set(value) {
            notifyPrefs.edit { putBoolean(AUTO_UPDATE, value) }
        }

    var autoUpdateAsked: Boolean
        get() = notifyPrefs.getBoolean(AUTO_UPDATE_ASKED, false)
        set(value) {
            notifyPrefs.edit { putBoolean(AUTO_UPDATE_ASKED, value) }
        }

    var batteryAsked: Boolean
        get() = notifyPrefs.getBoolean(BATTERY_ASKED, false)
        set(value) {
            notifyPrefs.edit { putBoolean(BATTERY_ASKED, value) }
        }

    var githubToken: String?
        get() = readSecret(GITHUB)
        set(value) {
            writeSecret(GITHUB, value?.trim()?.takeIf { it.isNotEmpty() })
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

    fun hasKey(): Boolean = !apiKey.isNullOrBlank()

    fun clear() {
        writeSecret(KEY, null)
        writeSecret(GITHUB, null)
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
        val found = readKey()
        if (!found.isNullOrEmpty()) {
            writeKey(found)
        }
        val github = readSecret(GITHUB)
        if (!github.isNullOrEmpty()) {
            writeSecret(GITHUB, github)
        }
    }

    private fun openEncrypted(context: Context, name: String): SharedPreferences? {
        val opened = runCatching { createEncrypted(context, name) }.getOrNull()
        if (opened != null) return opened
        runCatching { context.deleteSharedPreferences(name) }
        return runCatching { createEncrypted(context, name) }.getOrNull()
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

    companion object {
        private const val PREFS = "cursor_secure"
        private const val PREFS_BAK = "cursor_secure_bak"
        private const val PREFS_FALLBACK = "cursor_secure_fallback"
        private const val PREFS_NOTIFY = "cursor_prefs"
        private const val KEY = "api_key"
        private const val GITHUB = "github_token"
        private const val NOTIFY = "notify_on_complete"
        private const val NOTIFY_APPROVAL = "notify_on_approval"
        private const val SHOW_TOOLS = "show_tool_calls"
        private const val SHOW_THINKING = "show_thinking"
        private const val MCP_NAME = "mcp_name"
        private const val MCP_URL = "mcp_url"
        private const val MCP_LIST = "mcp_list"
        private const val DEFAULT_MODEL = "default_model"
        private const val SHOW_MIC = "show_microphone"
        private const val THEME_COLOR = "theme_color"
        private const val SHOW_INBOX_ENVS = "show_inbox_envs"
        private const val SHOW_INBOX_REMOTE = "show_inbox_remote"
        private const val AUTO_UPDATE = "auto_update"
        private const val AUTO_UPDATE_ASKED = "auto_update_asked"
        private const val BATTERY_ASKED = "battery_asked"
        const val DEFAULT_THEME_COLOR = 0xFFF54E00.toInt()
    }
}
