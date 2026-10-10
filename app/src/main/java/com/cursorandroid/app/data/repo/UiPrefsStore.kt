package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Key names are part of the on-disk format. They are string constants, so R8 renaming cannot touch them.
 * Never rename or reuse one; add a new key and a migration step instead.
 */
object UiKeys {
    val schemaVersion = intPreferencesKey("schema_version")

    val inboxWorkingOnly = booleanPreferencesKey("inbox_working_only")
    val inboxShowArchived = booleanPreferencesKey("inbox_archived_view")
    val inboxShowHidden = booleanPreferencesKey("inbox_show_hidden")
    val groupByRepo = booleanPreferencesKey("agent_group_by_repo")
    val compactCards = booleanPreferencesKey("agent_compact_cards")
    val hideFinishedDays = intPreferencesKey("agent_hide_finished_days")
    val collapsedRepos = stringSetPreferencesKey("agent_collapsed_repos")
    val repoGroupPrefs = stringPreferencesKey("agent_repo_group_prefs")
    val chatMeta = stringPreferencesKey("chat_meta")
    val chatMetaUnreadable = stringPreferencesKey("chat_meta_unreadable")
    val showInboxEnvs = booleanPreferencesKey("show_inbox_envs")
    val showInboxRemote = booleanPreferencesKey("show_inbox_remote")

    val themeColor = intPreferencesKey("theme_color")
    val uiFont = stringPreferencesKey("ui_font")
    val codeFont = stringPreferencesKey("code_font")
    val textScalePct = intPreferencesKey("text_scale_pct")
    val chatDensity = stringPreferencesKey("chat_density")

    val machinePrefs = stringPreferencesKey("machine_prefs")
    val machinePrefsUnreadable = stringPreferencesKey("machine_prefs_unreadable")
}

/** What the app kept in SharedPreferences before the DataStore existed. Read only, never deleted. */
class LegacyUiPrefs(
    val prefs: Map<String, Any?> = emptyMap(),
    val chatMetaJson: String? = null,
)

/**
 * One step lifts the stored data from [from] to `from + 1`. Steps run in order, so an app that
 * skipped several releases still walks every step. Add a step for every schema change; never edit
 * or remove an old one.
 */
class UiMigrationStep(val from: Int, val run: (MutablePreferences, LegacyUiPrefs) -> Unit)

const val UI_SCHEMA_VERSION = 2

private fun <T> MutablePreferences.seed(key: Preferences.Key<T>, value: Any?) {
    if (contains(key)) return
    @Suppress("UNCHECKED_CAST")
    val typed = value as? T ?: return
    this[key] = typed
}

@Suppress("UNCHECKED_CAST")
internal val schemaZeroToOne = UiMigrationStep(0) { p, legacy ->
    val old = legacy.prefs
    p.seed(UiKeys.inboxWorkingOnly, old["inbox_working_only"])
    p.seed(UiKeys.inboxShowArchived, old["inbox_archived_view"])
    p.seed(UiKeys.inboxShowHidden, old["inbox_show_hidden"])
    p.seed(UiKeys.groupByRepo, old["agent_group_by_repo"])
    p.seed(UiKeys.compactCards, old["agent_compact_cards"])
    p.seed(UiKeys.hideFinishedDays, old["agent_hide_finished_days"])
    p.seed(UiKeys.repoGroupPrefs, old["agent_repo_group_prefs"])
    p.seed(UiKeys.showInboxEnvs, old["show_inbox_envs"])
    p.seed(UiKeys.showInboxRemote, old["show_inbox_remote"])
    p.seed(UiKeys.themeColor, old["theme_color"])
    p.seed(UiKeys.uiFont, old["ui_font"])
    p.seed(UiKeys.codeFont, old["code_font"])
    p.seed(UiKeys.textScalePct, old["text_scale_pct"])
    p.seed(UiKeys.chatDensity, old["chat_density"])
    val collapsed = (old["agent_collapsed_repos"] as? Set<*>)?.filterIsInstance<String>()?.toSet()
    if (collapsed != null) p.seed(UiKeys.collapsedRepos, collapsed)
    p.seed(UiKeys.chatMeta, legacy.chatMetaJson)
}

/** Adds the machine list (hidden and forgotten machines, last seen times, auto-hide). Fills it only when missing. */
internal val schemaOneToTwo = UiMigrationStep(1) { p, _ ->
    p.seed(UiKeys.machinePrefs, "{}")
}

internal val uiMigrationSteps: List<UiMigrationStep> = listOf(schemaZeroToOne, schemaOneToTwo)

internal class UiPrefsMigration(
    private val legacy: LegacyUiPrefs,
    private val steps: List<UiMigrationStep>,
    private val target: Int,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[UiKeys.schemaVersion] ?: 0) < target

    override suspend fun migrate(currentData: Preferences): Preferences {
        val next = currentData.toMutablePreferences()
        var version = next[UiKeys.schemaVersion] ?: 0
        while (version < target) {
            steps.firstOrNull { it.from == version }?.run?.invoke(next, legacy)
            version += 1
        }
        next[UiKeys.schemaVersion] = target
        return next.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

/**
 * Agent-list, repo-group, pin, and appearance settings. They live in a Preferences DataStore file,
 * versioned with [UI_SCHEMA_VERSION]. Reads are synchronous from a memory copy loaded at start.
 * Writes update that copy at once and are queued to disk; [flush] waits for them.
 *
 * A file written by a newer app (a higher schema version) is never migrated down or cleared.
 */
class UiPrefsStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private var cache: Preferences = runBlocking { dataStore.data.first() }
    private var last: Job? = null

    val schemaVersion: Int get() = cache[UiKeys.schemaVersion] ?: 0

    operator fun <T> get(key: Preferences.Key<T>): T? = cache[key]

    fun <T> put(key: Preferences.Key<T>, value: T) = mutate { it[key] = value }

    fun <T> remove(key: Preferences.Key<T>) = mutate { it.remove(key) }

    fun flush() {
        val job = synchronized(lock) { last } ?: return
        runBlocking { job.join() }
    }

    fun close() {
        flush()
        runBlocking { (scope.coroutineContext[Job] ?: return@runBlocking).cancelAndJoin() }
    }

    private fun mutate(op: (MutablePreferences) -> Unit) {
        synchronized(lock) {
            cache = cache.toMutablePreferences().also(op).toPreferences()
            last = scope.launch { dataStore.edit { op(it) } }
        }
    }

    companion object {
        const val FILE = "ui_prefs"

        fun open(context: Context): UiPrefsStore {
            val app = context.applicationContext
            val legacyPrefs = app.getSharedPreferences("cursor_prefs", Context.MODE_PRIVATE)
            val legacyChats = app.getSharedPreferences("local_chats", Context.MODE_PRIVATE)
            val legacy = LegacyUiPrefs(
                prefs = legacyPrefs.all,
                chatMetaJson = firstReadableChatMeta(legacyChats.getString("meta", null), legacyPrefs.getString("chat_meta", null)),
            )
            return open(legacy, app.preferencesDataStoreFile(FILE))
        }

        /** The older store wrote `local_chats/meta` and mirrored it to `cursor_prefs/chat_meta`; use whichever still parses. */
        internal fun firstReadableChatMeta(vararg candidates: String?): String? {
            val present = candidates.filter { !it.isNullOrBlank() }
            return present.firstOrNull { raw ->
                runCatching { Json.parseToJsonElement(raw!!) is JsonObject }.getOrDefault(false)
            } ?: present.firstOrNull()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        internal fun open(
            legacy: LegacyUiPrefs,
            file: File,
            steps: List<UiMigrationStep> = uiMigrationSteps,
            target: Int = UI_SCHEMA_VERSION,
        ): UiPrefsStore {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                migrations = listOf(UiPrefsMigration(legacy, steps, target)),
                scope = scope,
                produceFile = { file },
            )
            return UiPrefsStore(store, scope)
        }
    }
}
