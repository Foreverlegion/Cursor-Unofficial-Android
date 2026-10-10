package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import com.cursorandroid.app.data.api.ModelSelection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The Cursor API only accepts a model on create and follow-up requests and does not echo it back
 * on agent or run responses, so the selection sent with each run is remembered locally.
 * A blank id marks a run that was started without an explicit model.
 */
@Serializable
data class RunModelBook(
    val runs: Map<String, ModelSelection> = emptyMap(),
    val latest: ModelSelection? = null,
    val agentApi: ModelSelection? = null,
) {
    /** Model the API reported or the app sent for this exact run. */
    fun forRun(runId: String?): ModelSelection? =
        runId?.let { runs[it] }?.takeIf { it.id.isNotBlank() }

    /** Agent-level guess used when the run itself has no known model. */
    fun fallback(runId: String?): ModelSelection? {
        val recorded = runId?.let { runs[it] }
        val agent = agentApi?.takeIf { it.id.isNotBlank() }
        return when {
            recorded != null -> agent
            else -> agent ?: latest?.takeIf { it.id.isNotBlank() }
        }
    }

    fun resolve(runId: String?): ModelSelection? = forRun(runId) ?: fallback(runId)

    fun withRun(runId: String, model: ModelSelection?, explicit: Boolean): RunModelBook {
        val known = model?.takeIf { it.id.isNotBlank() }
        if (known == null && !explicit) return this
        val next = LinkedHashMap(runs)
        next.remove(runId)
        next[runId] = known ?: ModelSelection("")
        while (next.size > MAX_RUNS) next.remove(next.keys.first())
        return copy(runs = next, latest = known ?: latest)
    }

    fun withApi(runModels: Map<String, ModelSelection>, agentModel: ModelSelection?): RunModelBook {
        val known = runModels.filterValues { it.id.isNotBlank() }
        val merged = if (known.isEmpty()) runs else {
            val next = LinkedHashMap(runs)
            known.forEach { (id, sel) ->
                next.remove(id)
                next[id] = sel
            }
            while (next.size > MAX_RUNS) next.remove(next.keys.first())
            next
        }
        val agent = agentModel?.takeIf { it.id.isNotBlank() } ?: agentApi
        return if (merged == runs && agent == agentApi) this else copy(runs = merged, agentApi = agent)
    }

    companion object {
        const val MAX_RUNS = 80
    }
}

class RunModelStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val gate = Any()

    fun load(agentId: String): RunModelBook = synchronized(gate) { read(agentId) }

    fun recordRun(agentId: String, runId: String, model: ModelSelection?, explicit: Boolean): RunModelBook =
        update(agentId) { it.withRun(runId, model, explicit) }

    fun recordApi(
        agentId: String,
        runModels: Map<String, ModelSelection>,
        agentModel: ModelSelection?,
    ): RunModelBook = update(agentId) { it.withApi(runModels, agentModel) }

    fun remove(agentId: String) {
        synchronized(gate) { prefs.edit { remove(key(agentId)) } }
    }

    private fun update(agentId: String, change: (RunModelBook) -> RunModelBook): RunModelBook {
        synchronized(gate) {
            val before = read(agentId)
            val after = change(before)
            if (after != before) {
                prefs.edit { putString(key(agentId), json.encodeToString(after)) }
            }
            return after
        }
    }

    private fun read(agentId: String): RunModelBook {
        val raw = prefs.getString(key(agentId), null) ?: return RunModelBook()
        return runCatching { json.decodeFromString<RunModelBook>(raw) }.getOrDefault(RunModelBook())
    }

    private fun key(agentId: String) = "book_$agentId"

    private companion object {
        const val PREFS = "cursor_run_models"
    }
}
