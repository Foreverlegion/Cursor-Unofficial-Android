package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Cached forge repo lists and per-forge sync status. Holds no tokens. */
class ForgeSyncStore(private val dir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()

    fun statuses(): Map<String, ForgeSyncStatus> = synchronized(lock) {
        runCatching {
            json.decodeFromString<Map<String, ForgeSyncStatus>>(File(dir, STATUS).readText())
        }.getOrDefault(emptyMap())
    }

    fun repos(forgeId: String): List<RepositoryItem>? = synchronized(lock) {
        val file = repoFile(forgeId)
        if (!file.isFile) return null
        runCatching { json.decodeFromString<List<RepositoryItem>>(file.readText()) }.getOrNull()
    }

    fun save(status: ForgeSyncStatus, repos: List<RepositoryItem>) = synchronized(lock) {
        dir.mkdirs()
        write(repoFile(status.forgeId), json.encodeToString(repos))
        write(File(dir, STATUS), json.encodeToString(statuses() + (status.forgeId to status)))
    }

    fun delete(forgeId: String) = synchronized(lock) {
        repoFile(forgeId).delete()
        val next = statuses() - forgeId
        if (File(dir, STATUS).isFile) write(File(dir, STATUS), json.encodeToString(next))
    }

    fun clear() = synchronized(lock) { dir.listFiles()?.forEach { it.delete() } }

    private fun repoFile(forgeId: String) = File(dir, "repos-${forgeId.replace(UNSAFE, "_")}.json")

    private fun write(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private companion object {
        const val STATUS = "status.json"
        val UNSAFE = Regex("[^A-Za-z0-9_-]")
    }
}

data class ForgeSyncState(
    val statuses: Map<String, ForgeSyncStatus> = emptyMap(),
    val reposByForge: Map<String, List<RepositoryItem>> = emptyMap(),
    val running: Set<String> = emptySet(),
) {
    fun repos(): List<RepositoryItem> = mergeRepos(*reposByForge.values.toTypedArray())
}

/**
 * Keeps each configured forge's repo list in step with the forge's own API, using the token saved in
 * Settings > Forges. Results are cached on disk and published as [state].
 */
class ForgeSyncer(
    private val store: ForgeSyncStore,
    private val forges: () -> List<ForgeConnection>,
    private val scope: CoroutineScope,
    private val transport: () -> ForgeTransport = { ForgeClient.transport },
    private val now: () -> Long = System::currentTimeMillis,
    private val enabled: () -> Boolean = { true },
) {
    private val flow = MutableStateFlow(ForgeSyncState())
    private val locks = ConcurrentHashMap<String, Mutex>()
    val state: StateFlow<ForgeSyncState> = flow.asStateFlow()

    @Volatile
    private var loaded = false

    @Synchronized
    fun load() {
        loaded = true
        val statuses = store.statuses()
        val repos = statuses.keys.mapNotNull { id -> store.repos(id)?.let { id to it } }.toMap()
        flow.update { it.copy(statuses = statuses, reposByForge = repos) }
    }

    fun requestLoad(): Job = scope.launch(Dispatchers.IO) { ensureLoaded() }

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    fun requestSync(forge: ForgeConnection): Job = scope.launch { sync(forge) }

    fun requestDue(maxAgeMs: Long, force: Boolean = false): Job = scope.launch { syncAll(maxAgeMs, force) }

    suspend fun sync(forge: ForgeConnection): ForgeSyncStatus? {
        val item = normalizedForge(forge)
        if (!enabled() || item.id.isBlank()) return null
        if (item.token.isBlank() || !forgeSyncSupported(item)) {
            forget(item.id)
            return null
        }
        withContext(Dispatchers.IO) { ensureLoaded() }
        return locks.getOrPut(item.id) { Mutex() }.withLock { run(item) }
    }

    suspend fun syncAll(maxAgeMs: Long = 0, force: Boolean = true) {
        if (!enabled()) return
        withContext(Dispatchers.IO) { ensureLoaded() }
        val ready = forges().map { normalizedForge(it) }
            .filter { it.id.isNotBlank() && it.token.isNotBlank() && forgeSyncSupported(it) }
        val keep = ready.map { it.id }.toSet()
        (flow.value.statuses.keys - keep).forEach { forget(it) }
        coroutineScope {
            ready.filter { force || due(it, maxAgeMs) }.map { async { sync(it) } }.awaitAll()
        }
    }

    fun forget(forgeId: String) {
        store.delete(forgeId)
        flow.update {
            it.copy(statuses = it.statuses - forgeId, reposByForge = it.reposByForge - forgeId)
        }
    }

    private fun due(forge: ForgeConnection, maxAgeMs: Long): Boolean {
        val status = flow.value.statuses[forge.id] ?: return true
        return status.error != null ||
            status.fingerprint != forgeFingerprint(forge) ||
            now() - status.syncedAt >= maxAgeMs
    }

    private suspend fun run(forge: ForgeConnection): ForgeSyncStatus {
        flow.update { it.copy(running = it.running + forge.id) }
        try {
            val fetched = try {
                withContext(Dispatchers.IO) { fetchForgeRepos(forge, transport()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ForgeFetch(emptyList(), error = "Sync failed (${e.javaClass.simpleName}).")
            }
            val before = flow.value
            val previous = before.reposByForge[forge.id]
            val repos = when {
                fetched.error == null -> fetched.repos
                previous != null -> previous
                else -> fetched.repos
            }
            val attempted = now()
            val status = ForgeSyncStatus(
                forgeId = forge.id,
                attemptedAt = attempted,
                syncedAt = if (fetched.error == null) attempted else before.statuses[forge.id]?.syncedAt ?: 0,
                repoCount = repos.size,
                error = fetched.error,
                authFailed = fetched.authFailed,
                truncated = fetched.truncated && fetched.error == null,
                fingerprint = forgeFingerprint(forge),
            )
            withContext(Dispatchers.IO) { store.save(status, repos) }
            flow.update {
                it.copy(
                    statuses = it.statuses + (forge.id to status),
                    reposByForge = it.reposByForge + (forge.id to repos),
                )
            }
            return status
        } finally {
            flow.update { it.copy(running = it.running - forge.id) }
        }
    }
}
