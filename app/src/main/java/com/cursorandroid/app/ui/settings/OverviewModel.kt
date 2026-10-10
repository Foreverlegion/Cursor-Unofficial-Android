package com.cursorandroid.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.MeResponse
import com.cursorandroid.app.data.api.WorkerPool
import com.cursorandroid.app.data.repo.AccountCounts
import com.cursorandroid.app.data.repo.AgentRepository
import com.cursorandroid.app.data.repo.CatalogCache
import com.cursorandroid.app.data.repo.UsageSample
import com.cursorandroid.app.data.repo.accountCounts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class OverviewRow { Account, Agents, Machines, Pools, Repos, Usage }

/**
 * Every row owns its request. Cached values show at once and each row swaps in its fresh value
 * the moment its own request returns, so the slowest request never blocks the others.
 */
internal class OverviewModel(
    private val repo: AgentRepository,
    private val catalog: CatalogCache,
) {
    var me by mutableStateOf(catalog.me())
        private set
    var agents by mutableStateOf(catalog.agents().takeIf { it.isNotEmpty() })
        private set
    var computers by mutableStateOf(catalog.computers().takeIf { it.isNotEmpty() })
        private set
    var pools by mutableStateOf(catalog.pools().takeIf { it.isNotEmpty() })
        private set
    var repoCount by mutableStateOf(catalog.repos().size.takeIf { it > 0 })
        private set
    var usage by mutableStateOf(catalog.usage())
        private set
    var refreshing by mutableStateOf(emptySet<OverviewRow>())
        private set
    var failed by mutableStateOf(emptySet<OverviewRow>())
        private set

    fun counts(): AccountCounts? {
        val list = agents ?: return null
        return accountCounts(list, computers.orEmpty(), pools.orEmpty(), repoCount ?: 0)
    }

    suspend fun refreshAll() {
        coroutineScope {
            launch { refreshAccount() }
            launch { refreshAgentsAndMachines() }
            launch { refreshPools() }
            launch { refreshRepos() }
            launch { refreshUsage() }
        }
    }

    suspend fun refreshAccount() = track(OverviewRow.Account) { me = repo.refreshMe() }

    suspend fun refreshAgentsAndMachines() {
        var list: List<AgentSummary>? = null
        track(OverviewRow.Agents) {
            list = repo.withinBudget(AGENTS_MS) { repo.refreshAgents() }
            agents = list
        }
        val known = list ?: agents ?: return
        track(OverviewRow.Machines) {
            val found: List<Computer> = repo.withinBudget(ROW_MS) { repo.refreshComputers(known) }
            computers = found
        }
    }

    suspend fun refreshPools() = track(OverviewRow.Pools) {
        val found: List<WorkerPool> = repo.withinBudget(ROW_MS) { repo.refreshPools() }
        pools = found
    }

    suspend fun refreshRepos() = track(OverviewRow.Repos) {
        repoCount = repo.withinBudget(ROW_MS) { repo.refreshRepos() }.size
    }

    suspend fun refreshUsage() = track(OverviewRow.Usage) {
        val sample: UsageSample = repo.recentUsage()
        usage = sample
    }

    private suspend fun track(row: OverviewRow, block: suspend () -> Unit) {
        refreshing = refreshing + row
        try {
            block()
            failed = failed - row
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = failed + row
        } finally {
            refreshing = refreshing - row
        }
    }

    private companion object {
        const val AGENTS_MS = 40_000L
        const val ROW_MS = 20_000L
    }
}

internal fun MeResponse?.displayLine(): String? {
    val me = this ?: return null
    return listOfNotNull(
        me.userEmail,
        listOfNotNull(me.userFirstName, me.userLastName).joinToString(" ").ifBlank { null },
    ).joinToString(" · ").ifBlank { me.apiKeyName }
}
