package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.repo.FolderMembership
import com.cursorandroid.app.data.repo.LocalFolder
import com.cursorandroid.app.data.repo.RepoGroupPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderLayoutTest {
    private val now = java.time.Instant.parse("2026-10-10T12:00:00Z").toEpochMilli()

    @Test
    fun folderRunningCountsLooseChatsAndNestedGroups() {
        val git = mapOf(
            "run" to GitSnap("run", repoUrl = "https://github.com/acme/app"),
            "idle" to GitSnap("idle", repoUrl = "https://github.com/acme/app"),
        )
        val folders = FolderMembership(
            folders = listOf(LocalFolder(id = "f1", name = "Work")),
            chats = emptyMap(),
            groups = mapOf("acme/app" to "f1"),
        )
        val arranged = arrangeCloudAgents(
            agents = listOf(
                agent("run", "RUNNING"),
                agent("idle", "FINISHED"),
            ),
            git = git,
            pinnedAt = emptyMap(),
            favoriteIds = emptySet(),
            approvalIds = emptySet(),
            filter = CloudFilter.All,
            hideFinishedDays = 0,
            revealFinished = false,
            groupByRepo = true,
            nowMillis = now,
            groupPrefs = RepoGroupPrefs(),
            folders = folders,
        )
        val laid = applyLocalFolders(arranged, folders, approvalIds = emptySet())
        assertEquals(1, laid.folders.single().running)
    }

    @Test
    fun folderRunningIncludesChatMovedOutOfRepoGroup() {
        val git = mapOf(
            "solo" to GitSnap("solo", repoUrl = "https://github.com/acme/docs"),
        )
        val folders = FolderMembership(
            folders = listOf(LocalFolder(id = "f1", name = "Side")),
            chats = mapOf("solo" to "f1"),
            groups = emptyMap(),
        )
        val arranged = arrangeCloudAgents(
            agents = listOf(agent("solo", "RUNNING")),
            git = git,
            pinnedAt = emptyMap(),
            favoriteIds = emptySet(),
            approvalIds = emptySet(),
            filter = CloudFilter.All,
            hideFinishedDays = 0,
            revealFinished = false,
            groupByRepo = true,
            nowMillis = now,
            groupPrefs = RepoGroupPrefs(),
            folders = folders,
        )
        val laid = applyLocalFolders(arranged, folders, approvalIds = emptySet())
        assertEquals(1, laid.folders.single().running)
    }

    private fun agent(id: String, status: String) =
        AgentSummary(id = id, name = id, status = status, updatedAt = "2026-10-10T00:00:00Z")
}
