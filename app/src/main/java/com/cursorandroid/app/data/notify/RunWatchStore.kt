package com.cursorandroid.app.data.notify

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RunWatchStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun add(context: Context, item: WatchItem) {
        val next = all(context).filterNot { it.runId == item.runId } + item
        save(context, next)
    }

    fun remove(context: Context, runId: String) {
        save(context, all(context).filterNot { it.runId == runId })
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    fun all(context: Context): List<WatchItem> {
        val raw = prefs(context).getString(ALL, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<WatchItem>>(raw) }.getOrDefault(emptyList())
    }

    fun isEmpty(context: Context): Boolean = all(context).isEmpty()

    private fun save(context: Context, items: List<WatchItem>) {
        prefs(context).edit { putString(ALL, json.encodeToString(items)) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "run_watch"
    private const val ALL = "items"
}

@Serializable
data class WatchItem(
    val agentId: String,
    val runId: String,
    val agentName: String? = null,
)
