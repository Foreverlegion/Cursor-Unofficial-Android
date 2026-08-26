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
    fun visibleInboxHidesArchivedUnlessAsked() {
        val live = agent("live", archived = false)
        val archived = agent("archived", archived = true)

        assertEquals(listOf("live"), listOf(live, archived).visibleInbox(showArchived = false).map { it.id })
        assertEquals(listOf("live", "archived"), listOf(live, archived).visibleInbox(showArchived = true).map { it.id })
    }

    @Test
    fun visibleInboxHidesLocalHiddenUnlessAsked() {
        val shown = agent("shown")
        val hidden = agent("hidden")

        assertEquals(
            listOf("shown"),
            listOf(shown, hidden).visibleInbox(
                showArchived = true,
                hiddenIds = setOf("hidden"),
                showHidden = false,
            ).map { it.id },
        )
        assertEquals(
            listOf("shown", "hidden"),
            listOf(shown, hidden).visibleInbox(
                showArchived = true,
                hiddenIds = setOf("hidden"),
                showHidden = true,
            ).map { it.id },
        )
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

    private fun agent(
        id: String,
        updatedAt: String? = null,
        archived: Boolean? = null,
        status: String? = null,
    ) = AgentSummary(
        id = id,
        name = id,
        status = status,
        updatedAt = updatedAt,
        archived = archived,
    )
}
