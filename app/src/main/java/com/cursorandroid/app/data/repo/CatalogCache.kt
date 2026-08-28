package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.api.RepositoryItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CatalogCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun agents(): List<AgentSummary> = readList("agents")
    fun computers(): List<Computer> = readList("computers")
    fun repos(): List<RepositoryItem> = readList("repos")
    fun cloudEnvs(): List<String> = readList("cloud_envs")

    fun saveAgents(items: List<AgentSummary>) = write("agents", items)
    fun saveComputers(items: List<Computer>) = write("computers", items)
    fun saveRepos(items: List<RepositoryItem>) = write("repos", items)

    fun rememberCloudEnv(name: String) {
        val next = name.trim()
        if (next.isEmpty() || next.equals("Cloud", ignoreCase = true)) return
        val merged = (cloudEnvs() + next).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        write("cloud_envs", merged)
    }

    fun gitSnaps(): Map<String, GitSnap> {
        val raw = prefs.getString("git", null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, GitSnap>>(raw) }.getOrDefault(emptyMap())
    }

    fun saveGit(snap: GitSnap) {
        val next = gitSnaps().toMutableMap()
        next[snap.agentId] = snap
        prefs.edit { putString("git", json.encodeToString(next)) }
    }

    fun removeGit(agentId: String) {
        val next = gitSnaps().toMutableMap()
        if (next.remove(agentId) != null) {
            prefs.edit { putString("git", json.encodeToString(next)) }
        }
    }

    fun reposFresh(maxAgeMs: Long = REPOS_TTL): Boolean {
        val at = prefs.getLong("repos_at", 0L)
        return at > 0L && System.currentTimeMillis() - at < maxAgeMs && repos().isNotEmpty()
    }

    fun branches(url: String): List<String> {
        val raw = prefs.getString(branchKey(url), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    fun saveBranches(url: String, names: List<String>) {
        prefs.edit {
            putString(branchKey(url), json.encodeToString(names))
            putLong(branchKey(url) + "_at", System.currentTimeMillis())
        }
    }

    fun branchesFresh(url: String, maxAgeMs: Long = BRANCH_TTL): Boolean {
        val at = prefs.getLong(branchKey(url) + "_at", 0L)
        return at > 0L && System.currentTimeMillis() - at < maxAgeMs && branches(url).isNotEmpty()
    }

    private inline fun <reified T> readList(key: String): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
    }

    private inline fun <reified T> write(key: String, value: List<T>) {
        prefs.edit {
            putString(key, json.encodeToString(value))
            putLong("${key}_at", System.currentTimeMillis())
        }
    }

    private fun branchKey(url: String) = "br_${url.hashCode()}"

    companion object {
        private const val PREFS = "catalog_cache"
        const val REPOS_TTL = 30L * 60L * 1000L
        const val BRANCH_TTL = 15L * 60L * 1000L
    }
}
