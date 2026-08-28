package com.cursorandroid.app.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxAgentsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mergeKeepsOlderChatsWhenFirstPageRefreshes() {
        val older = agent("old", updatedAt = "2024-01-01T00:00:00.000Z")
        val newer = agent("new", updatedAt = "2026-08-26T00:00:00.000Z")
        val updated = newer.copy(status = "FINISHED")

        val merged = mergeInboxAgents(listOf(newer, older), listOf(updated))

        assertEquals(listOf("new", "old"), merged.map { it.id })
        assertEquals("FINISHED", merged[0].status)
    }

    @Test
    fun mergeReplacesStaleIdleWithActive() {
        val stale = agent("a", status = "IDLE")
        val fresh = agent("a", status = "ACTIVE")
        val merged = mergeInboxAgents(listOf(stale), listOf(fresh))
        assertEquals("ACTIVE", merged.single().status)
    }

    @Test
    fun withDetailReplacesStaleIdle() {
        val stale = agent("a", status = "IDLE")
        val detail = AgentDetail(id = "a", name = "a", status = "ACTIVE", latestRunId = "run-1")
        assertEquals("ACTIVE", stale.withDetail(detail).status)
        assertEquals("run-1", stale.withDetail(detail).latestRunId)
    }

    @Test
    fun visibleInboxShowsArchivedOnlyWhenSelected() {
        val live = agent("live", archived = false)
        val archived = agent("archived", archived = true)

        assertEquals(listOf("live"), listOf(live, archived).visibleInbox(showArchived = false).map { it.id })
        assertEquals(listOf("archived"), listOf(live, archived).visibleInbox(showArchived = true).map { it.id })
    }

    @Test
    fun visibleInboxShowsHiddenOnlyWhenSelected() {
        val shown = agent("shown")
        val hidden = agent("hidden")

        assertEquals(
            listOf("shown"),
            listOf(shown, hidden).visibleInbox(
                showArchived = false,
                hiddenIds = setOf("hidden"),
                showHidden = false,
            ).map { it.id },
        )
        assertEquals(
            listOf("hidden"),
            listOf(shown, hidden).visibleInbox(
                showArchived = false,
                hiddenIds = setOf("hidden"),
                showHidden = true,
            ).map { it.id },
        )
    }

    @Test
    fun cloudStatusArchivedCountsAsArchived() {
        val cloud = agent("cloud", status = "ARCHIVED")
        assertTrue(cloud.isArchived())
        assertEquals(listOf<String>(), listOf(cloud).visibleInbox(showArchived = false).map { it.id })
        assertEquals(listOf("cloud"), listOf(cloud).visibleInbox(showArchived = true).map { it.id })
    }

    @Test
    fun markCloudArchivedUsesActiveListGap() {
        val kept = agent("kept")
        val gone = agent("gone")
        val marked = markCloudArchived(listOf(kept, gone), listOf(kept))
        assertEquals(false, marked.first { it.id == "kept" }.archived)
        assertEquals(true, marked.first { it.id == "gone" }.archived)
    }

    @Test
    fun mergeKeepsKnownArchivedFlag() {
        val existing = agent("a", archived = true)
        val incoming = agent("a", archived = null, status = "FINISHED")
        val merged = mergeInboxAgents(listOf(existing), listOf(incoming))
        assertTrue(merged.single().isArchived())
    }

    @Test
    fun foldPagesWalksCursorsAndKeepsOlderItems() {
        val page1 = AgentListResponse(
            items = listOf(agent("new", updatedAt = "2026-08-26T00:00:00.000Z")),
            nextCursor = "page2",
        )
        val page2 = AgentListResponse(
            items = listOf(agent("old", updatedAt = "2024-01-01T00:00:00.000Z")),
        )

        val folded = foldAgentPages(listOf(page1, page2))

        assertEquals(setOf("new", "old"), folded.items.map { it.id }.toSet())
        assertNull(folded.nextCursor)
    }

    @Test
    fun foldPagesKeepsCursorWhenCapped() {
        val pages = listOf(
            AgentListResponse(items = listOf(agent("a")), nextCursor = "b"),
            AgentListResponse(items = listOf(agent("b")), nextCursor = "c"),
        )

        val folded = foldAgentPages(pages, maxPages = 2)

        assertEquals(setOf("a", "b"), folded.items.map { it.id }.toSet())
        assertEquals("c", folded.nextCursor)
    }

    @Test
    fun listResponseReadsItemsOrAgents() {
        val fromItems = json.decodeFromString<AgentListResponse>(
            """{"items":[{"id":"bc-1","name":"From items"}],"nextCursor":"next"}""",
        )
        val fromAgents = json.decodeFromString<AgentListResponse>(
            """{"agents":[{"id":"bc-2","name":"From agents"}]}""",
        )

        assertEquals(listOf("bc-1"), fromItems.entries().map { it.id })
        assertEquals("next", fromItems.nextCursor)
        assertEquals(listOf("bc-2"), fromAgents.entries().map { it.id })
        assertTrue(fromAgents.nextCursor.isNullOrBlank())
    }

    @Test
    fun localAndMachineEnvsGoToRemoteNotEnvs() {
        val cloud = agent("cloud", env = Env(type = "cloud"))
        val pool = agent("pool", env = Env(type = "pool", name = "default"))
        val machine = agent("pc", env = Env(type = "machine", name = "Office-PC"))
        val local = agent("local", env = Env(type = "local", name = "Laptop"))
        val all = listOf(cloud, pool, machine, local)

        assertEquals(listOf("Cloud", "default"), all.hostedEnvs().map { it.name })
        assertEquals(listOf("Laptop", "Office-PC"), all.remoteEnvs().map { it.name }.sorted())
        assertTrue(isRemoteEnvType("local"))
        assertTrue(isRemoteEnvType("machine"))
        assertEquals("machine", all.remoteEnvs().first().composeType())
    }

    @Test
    fun namedCloudEnvironmentsSkipGenericCloud() {
        val generic = agent("a", env = Env(type = "cloud"))
        val named = agent("b", env = Env(type = "cloud", name = "thermal-nexus"))
        val machine = agent("c", env = Env(type = "machine", name = "Office-PC"))
        assertEquals(
            listOf("saved", "thermal-nexus"),
            listOf(generic, named, machine).namedCloudEnvironments(listOf("saved", "Cloud")),
        )
    }

    @Test
    fun cloudCreateUsesNamedEnvWithoutRepos() {
        val (env, repos) = cloudCreateTarget(true, "thermal-nexus", "https://github.com/acme/app", "main")
        assertEquals("cloud", env?.type)
        assertEquals("thermal-nexus", env?.name)
        assertNull(repos)
    }

    @Test
    fun cloudCreateFromRepoOmitsEnv() {
        val (env, repos) = cloudCreateTarget(false, "", "https://github.com/acme/app", "main")
        assertNull(env)
        assertEquals("https://github.com/acme/app", repos?.single()?.url)
        assertEquals("main", repos?.single()?.startingRef)
    }

    private fun agent(
        id: String,
        updatedAt: String? = null,
        archived: Boolean? = null,
        status: String? = null,
        env: Env? = null,
    ) = AgentSummary(
        id = id,
        name = id,
        status = status,
        updatedAt = updatedAt,
        archived = archived,
        env = env,
    )
}
