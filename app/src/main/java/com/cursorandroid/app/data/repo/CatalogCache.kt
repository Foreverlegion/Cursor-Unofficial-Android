package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.CloudEnvironment
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.api.WorkerPool
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CatalogCache(
    context: Context,
    private val demo: () -> Boolean = { false },
) {
    private val app = context.applicationContext
    private val real = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val demoPrefs = app.getSharedPreferences(PREFS_DEMO, Context.MODE_PRIVATE)
    private fun active() = if (demo()) demoPrefs else real
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun agents(): List<AgentSummary> = readList("agents")
    fun computers(): List<Computer> = readList("computers")
    fun repos(): List<RepositoryItem> = readList("repos")
    fun pools(): List<WorkerPool> = readList("pools")
    fun cloudEnvs(): List<String> = readList("cloud_envs")
    fun savedEnvironments(): List<CloudEnvironment> = readList("saved_environments")

    fun saveAgents(items: List<AgentSummary>) = write("agents", items)
    fun saveComputers(items: List<Computer>) = write("computers", items)
    fun saveRepos(items: List<RepositoryItem>) = write("repos", items)
    fun savePools(items: List<WorkerPool>) = write("pools", items)

    fun rememberCloudEnv(name: String) {
        val next = name.trim()
        if (next.isEmpty() || next.equals("Cloud", ignoreCase = true)) return
        val merged = (cloudEnvs() + next).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        write("cloud_envs", merged)
    }

    fun rememberEnvironment(env: CloudEnvironment) {
        if (env.id.isBlank() && env.name.isBlank()) return
        val current = savedEnvironments()
        val next = if (env.id.isBlank()) {
            current.filterNot { it.name.equals(env.name, ignoreCase = true) } + env
        } else {
            current.filterNot { it.id == env.id } + env
        }
        write("saved_environments", next.sortedBy { it.name.lowercase() })
        if (env.name.isNotBlank()) rememberCloudEnv(env.name)
    }

    fun forgetEnvironment(id: String) {
        if (id.isBlank()) return
        write("saved_environments", savedEnvironments().filterNot { it.id == id })
    }

    fun gitSnaps(): Map<String, GitSnap> {
        val raw = active().getString("git", null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, GitSnap>>(raw) }.getOrDefault(emptyMap())
    }

    fun saveGit(snap: GitSnap) {
        val next = gitSnaps().toMutableMap()
        next[snap.agentId] = snap
        active().edit { putString("git", json.encodeToString(next)) }
    }

    fun removeGit(agentId: String) {
        val next = gitSnaps().toMutableMap()
        if (next.remove(agentId) != null) {
            active().edit { putString("git", json.encodeToString(next)) }
        }
    }

    fun reposFresh(maxAgeMs: Long = REPOS_TTL): Boolean {
        val at = active().getLong("repos_at", 0L)
        return at > 0L && System.currentTimeMillis() - at < maxAgeMs && repos().isNotEmpty()
    }

    fun branches(url: String): List<String> {
        val raw = active().getString(branchKey(url), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    fun saveBranches(url: String, names: List<String>) {
        active().edit {
            putString(branchKey(url), json.encodeToString(names))
            putLong(branchKey(url) + "_at", System.currentTimeMillis())
        }
    }

    fun branchesFresh(url: String, maxAgeMs: Long = BRANCH_TTL): Boolean {
        val at = active().getLong(branchKey(url) + "_at", 0L)
        return at > 0L && System.currentTimeMillis() - at < maxAgeMs && branches(url).isNotEmpty()
    }

    private inline fun <reified T> readList(key: String): List<T> {
        val raw = active().getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
    }

    private inline fun <reified T> write(key: String, value: List<T>) {
        active().edit {
            putString(key, json.encodeToString(value))
            putLong("${key}_at", System.currentTimeMillis())
        }
    }

    private fun branchKey(url: String) = "br_${url.hashCode()}"

    companion object {
        private const val PREFS = "catalog_cache"
        private const val PREFS_DEMO = "catalog_cache_demo"
        const val REPOS_TTL = 30L * 60L * 1000L
        const val BRANCH_TTL = 15L * 60L * 1000L
    }
}
