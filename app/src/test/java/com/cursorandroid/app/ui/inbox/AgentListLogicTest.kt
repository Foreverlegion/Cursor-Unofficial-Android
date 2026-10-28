package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.api.visibleInbox
import com.cursorandroid.app.data.repo.FolderMembership
import com.cursorandroid.app.data.repo.LocalFolder
import com.cursorandroid.app.data.repo.RepoGroupPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentListLogicTest {
    private val now = InstantMillis.of("2026-10-10T12:00:00Z")

    @Test
    fun filtersRunningAttentionAndDone() {
        val running = agent("run", status = "RUNNING")
        val failed = agent("bad", status = "ERROR")
        val waiting = agent("wait", status = "FINISHED")
        val done = agent("done", status = "FINISHED")
        val all = listOf(running, failed, waiting, done)
        val approval = setOf("wait")
        assertEquals(listOf("run"), shown(all, CloudFilter.Running, approval))
        assertEquals(listOf("bad", "wait"), shown(all, CloudFilter.NeedsAttention, approval))
        assertEquals(listOf("done"), shown(all, CloudFilter.Done, approval))
        assertEquals(4, shown(all, CloudFilter.All, approval).size)
    }

    @Test
    fun hidesFinishedOlderThanTheChosenWindow() {
        val fresh = agent("fresh", status = "FINISHED", updatedAt = "2026-10-10T01:00:00Z")
        val old = agent("old", status = "FINISHED", updatedAt = "2026-10-06T01:00:00Z")
        val stillRunning = agent("live", status = "RUNNING", updatedAt = "2026-10-01T01:00:00Z")
        val errored = agent("bad", status = "ERROR", updatedAt = "2026-10-01T01:00:00Z")
        val arranged = arrange(
            listOf(fresh, old, stillRunning, errored),
            hideDays = 3,
        )
        assertEquals(listOf("fresh", "live", "bad"), (arranged.rest).map { it.id })
        assertEquals(1, arranged.hiddenFinished)
        val revealed = arrange(listOf(fresh, old, stillRunning, errored), hideDays = 3, reveal = true)
        assertTrue(revealed.rest.map { it.id }.contains("old"))
    }

    @Test
    fun groupsByRepoNewestActivityFirst() {
        val git = mapOf(
            "a" to GitSnap("a", repoUrl = "https://github.com/acme/app"),
            "b" to GitSnap("b", repoUrl = "https://github.com/acme/app"),
            "c" to GitSnap("c", repoUrl = "https://github.com/acme/docs"),
        )
        val arranged = arrange(
            listOf(
                agent("a", updatedAt = "2026-10-01T00:00:00Z"),
                agent("b", updatedAt = "2026-10-08T00:00:00Z"),
                agent("c", updatedAt = "2026-10-10T00:00:00Z"),
            ),
            git = git,
            group = true,
        )
        assertEquals(listOf("docs", "app"), arranged.groups.map { it.label })
        assertEquals(listOf("c"), arranged.groups[0].agents.map { it.id })
        assertEquals(listOf("b", "a"), arranged.groups[1].agents.map { it.id })
        assertEquals(0, arranged.groups[0].running)
    }

    @Test
    fun runningCountShowsOnTheRepoHeader() {
        val git = mapOf("a" to GitSnap("a", repoUrl = "https://github.com/acme/app"))
        val arranged = arrange(
            listOf(agent("a", status = "RUNNING", updatedAt = "2026-10-10T00:00:00Z")),
            git = git,
            group = true,
        )
        assertEquals(1, arranged.groups.single().running)
        assertEquals("app", arranged.groups.single().label)
    }

    @Test
    fun pinsStayAboveRepoGroups() {
        val git = mapOf(
            "old" to GitSnap("old", repoUrl = "https://github.com/acme/app"),
            "new" to GitSnap("new", repoUrl = "https://github.com/acme/docs"),
        )
        val arranged = arrange(
            listOf(
                agent("old", updatedAt = "2026-10-01T00:00:00Z"),
                agent("new", updatedAt = "2026-10-10T00:00:00Z"),
            ),
            git = git,
            group = true,
            pinnedAt = mapOf("old" to 50L),
        )
        assertEquals(listOf("old"), arranged.pinned.map { it.id })
        assertEquals(listOf("new"), arranged.groups.single().agents.map { it.id })
    }

    @Test
    fun starredRepoIsNeverCollapsedIntoTheAgeOverflow() {
        val git = mapOf(
            "star-old" to GitSnap("star-old", repoUrl = "https://github.com/acme/starred"),
            "star-new" to GitSnap("star-new", repoUrl = "https://github.com/acme/starred"),
            "plain-old" to GitSnap("plain-old", repoUrl = "https://github.com/acme/plain"),
            "plain-new" to GitSnap("plain-new", repoUrl = "https://github.com/acme/plain"),
            "star-run" to GitSnap("star-run", repoUrl = "https://github.com/acme/starred"),
        )
        val prefs = RepoGroupPrefs()
            .withOrder(listOf("acme/starred", "acme/plain"))
            .withStyle("acme/starred") { it.copy(favorite = true) }
        val agents = listOf(
            agent("star-old", updatedAt = "2026-09-01T00:00:00Z"),
            agent("star-new", updatedAt = "2026-10-10T08:00:00Z"),
            agent("plain-old", updatedAt = "2026-09-01T00:00:00Z"),
            agent("plain-new", updatedAt = "2026-10-10T09:00:00Z"),
            agent("star-run", status = "RUNNING", updatedAt = "2026-08-01T00:00:00Z"),
        )
        val all = arrange(agents, git = git, hideDays = 7, group = true, prefs = prefs)
        assertEquals(listOf("acme/starred", "acme/plain"), all.groups.map { it.key })
        assertEquals(listOf("star-new", "star-old", "star-run"), all.groups[0].agents.map { it.id })
        assertEquals(listOf("plain-new"), all.groups[1].agents.map { it.id })
        assertEquals(listOf("plain-old"), all.groups[1].hiddenOlder.map { it.id })
        assertEquals(1, all.hiddenFinished)

        val done = arrange(agents, git = git, filter = CloudFilter.Done, hideDays = 7, group = true, prefs = prefs)
        assertEquals(listOf("star-new", "star-old"), done.groups.single { it.key == "acme/starred" }.agents.map { it.id })
        assertEquals(listOf("plain-new"), done.groups.single { it.key == "acme/plain" }.agents.map { it.id })
        assertTrue(done.hiddenFinished >= 1)

        val running = arrange(agents, git = git, filter = CloudFilter.Running, hideDays = 7, group = true, prefs = prefs)
        assertEquals(listOf("star-run"), running.groups.single().agents.map { it.id })
        assertEquals(0, running.hiddenFinished)

        val searched = arrange(
            agents.filter { it.id == "star-old" || it.id == "plain-old" },
            git = git,
            hideDays = 7,
            group = true,
            prefs = prefs,
        )
        assertEquals(listOf("star-old"), searched.groups.flatMap { it.agents }.map { it.id })
        assertEquals(1, searched.hiddenFinished)

        val flat = arrange(agents, git = git, hideDays = 7, prefs = prefs)
        assertEquals(listOf("star-old", "star-new", "star-run", "plain-new"), flat.rest.map { it.id })
        assertEquals(1, flat.hiddenFinished)

        val attentionGit = git + mapOf(
            "star-bad" to GitSnap("star-bad", repoUrl = "https://github.com/acme/starred"),
            "plain-bad" to GitSnap("plain-bad", repoUrl = "https://github.com/acme/plain"),
        )
        val attention = arrange(
            agents + listOf(
                agent("star-bad", status = "ERROR", updatedAt = "2026-08-01T00:00:00Z"),
                agent("plain-bad", status = "ERROR", updatedAt = "2026-08-01T00:00:00Z"),
            ),
            git = attentionGit,
            filter = CloudFilter.NeedsAttention,
            hideDays = 7,
            group = true,
            prefs = prefs,
        )
        assertEquals(listOf("star-bad"), attention.groups.single { it.key == "acme/starred" }.agents.map { it.id })
        assertEquals(listOf("plain-bad"), attention.groups.single { it.key == "acme/plain" }.agents.map { it.id })
        assertEquals(0, attention.hiddenFinished)
    }

    @Test
    fun explicitHideStillRemovesAChatInAStarredRepo() {
        val git = mapOf("star-old" to GitSnap("star-old", repoUrl = "https://github.com/acme/starred"))
        val prefs = RepoGroupPrefs().withStyle("acme/starred") { it.copy(favorite = true) }
        val visible = listOf(agent("star-old", updatedAt = "2026-09-01T00:00:00Z"))
            .visibleInbox(showArchived = false, hiddenIds = setOf("star-old"))
        val arranged = arrange(visible, git = git, hideDays = 7, group = true, prefs = prefs)
        assertTrue(arranged.groups.isEmpty())
        assertEquals(0, arranged.hiddenFinished)
    }

    @Test
    fun starredFolderStaysOutOfTheAgeOverflowAndSortsFirst() {
        val git = mapOf(
            "old" to GitSnap("old", repoUrl = "https://github.com/acme/thermal-nexus"),
            "other" to GitSnap("other", repoUrl = "https://github.com/acme/plain"),
        )
        val folders = FolderMembership(
            folders = listOf(
                LocalFolder(id = "plain", name = "Other", createdAt = 1),
                LocalFolder(id = "star", name = "Thermal Nexus", createdAt = 2, favorite = true),
            ),
            groups = mapOf("acme/thermal-nexus" to "star", "acme/plain" to "plain"),
        )
        val agents = listOf(
            agent("old", updatedAt = "2026-09-01T00:00:00Z"),
            agent("other", updatedAt = "2026-09-01T00:00:00Z"),
        )
        val arranged = arrange(agents, git = git, hideDays = 7, group = true, folders = folders)
        assertEquals(listOf("old"), arranged.groups.flatMap { it.agents }.map { it.id })
        assertEquals(1, arranged.hiddenFinished)
        val laid = applyLocalFolders(arranged, folders, emptySet())
        assertEquals(listOf("star", "plain"), laid.folders.map { it.folder.id })
        assertEquals(listOf("old"), laid.folders.first().groups.flatMap { it.agents }.map { it.id })
        assertTrue(laid.folders[1].groups.isEmpty())
    }

    @Test
    fun twoStarredReposKeepTheirOrderAboveANewerUnstarredRepo() {
        val git = mapOf(
            "g" to GitSnap("g", repoUrl = "https://github.com/acme/gamma"),
            "a" to GitSnap("a", repoUrl = "https://github.com/acme/alpha"),
            "b" to GitSnap("b", repoUrl = "https://github.com/acme/beta"),
        )
        val prefs = RepoGroupPrefs()
            .withOrder(listOf("acme/gamma", "acme/alpha", "acme/beta"))
            .withStyle("acme/gamma") { it.copy(favorite = true) }
            .withStyle("acme/alpha") { it.copy(favorite = true) }
        val arranged = arrange(
            listOf(
                agent("g", updatedAt = "2026-09-01T00:00:00Z"),
                agent("a", updatedAt = "2026-09-02T00:00:00Z"),
                agent("b", updatedAt = "2026-10-10T00:00:00Z"),
            ),
            git = git,
            hideDays = 7,
            group = true,
            prefs = prefs,
        )
        assertEquals(listOf("acme/gamma", "acme/alpha", "acme/beta"), arranged.groups.map { it.key })
        assertEquals(0, arranged.hiddenFinished)
    }

    @Test
    fun aChatMovedToAnotherFolderLeavesItsRepoGroup() {
        val git = mapOf(
            "in" to GitSnap("in", repoUrl = "https://github.com/acme/app"),
            "out" to GitSnap("out", repoUrl = "https://github.com/acme/app"),
        )
        val folders = FolderMembership(
            folders = listOf(
                LocalFolder(id = "home", name = "Home", createdAt = 1),
                LocalFolder(id = "other", name = "Other", createdAt = 2),
            ),
            groups = mapOf("acme/app" to "home"),
            chats = mapOf("out" to "other"),
        )
        val arranged = arrange(
            listOf(
                agent("in", updatedAt = "2026-10-10T00:00:00Z"),
                agent("out", updatedAt = "2026-10-09T00:00:00Z"),
            ),
            git = git,
            group = true,
            folders = folders,
        )
        val laid = applyLocalFolders(arranged, folders, emptySet())
        assertEquals(listOf("in"), laid.folders.single { it.folder.id == "home" }.groups.flatMap { it.agents }.map { it.id })
        assertEquals(listOf("out"), laid.folders.single { it.folder.id == "other" }.chats.map { it.id })
        assertTrue(laid.groups.isEmpty())
    }

    @Test
    fun missingRepoSharesOneGroup() {
        val arranged = arrange(
            listOf(agent("a", updatedAt = "2026-10-02T00:00:00Z"), agent("b", updatedAt = "2026-10-03T00:00:00Z")),
            group = true,
        )
        assertEquals("No repo", arranged.groups.single().label)
        assertEquals(listOf("b", "a"), arranged.groups.single().agents.map { it.id })
    }

    private fun shown(agents: List<AgentSummary>, filter: CloudFilter, approval: Set<String>): List<String> {
        return arrange(agents, filter = filter, approval = approval).rest.map { it.id }
    }

    private fun arrange(
        agents: List<AgentSummary>,
        git: Map<String, GitSnap> = emptyMap(),
        filter: CloudFilter = CloudFilter.All,
        approval: Set<String> = emptySet(),
        hideDays: Int = 0,
        reveal: Boolean = false,
        group: Boolean = false,
        pinnedAt: Map<String, Long> = emptyMap(),
        favoriteIds: Set<String> = emptySet(),
        prefs: RepoGroupPrefs = RepoGroupPrefs(),
        folders: FolderMembership = FolderMembership(),
        applyAgeFilter: Boolean = true,
        unreadAgentIds: Set<String> = emptySet(),
    ): CloudArrangement {
        return arrangeCloudAgents(
            agents = agents,
            git = git,
            pinnedAt = pinnedAt,
            favoriteIds = favoriteIds,
            approvalIds = approval,
            filter = filter,
            hideFinishedDays = hideDays,
            revealFinished = reveal,
            groupByRepo = group,
            nowMillis = now,
            groupPrefs = prefs,
            folders = folders,
            unreadAgentIds = unreadAgentIds,
            applyAgeFilter = applyAgeFilter,
        )
    }

    @Test
    fun offKeepsOldAgentsVisible() {
        val old = agent("old", status = "FINISHED", updatedAt = "2026-09-01T00:00:00Z")
        val arranged = arrange(listOf(old), hideDays = 0, group = true)
        assertEquals(listOf("old"), arranged.groups.single().agents.map { it.id })
        assertEquals(0, arranged.hiddenFinished)
    }

    @Test
    fun pinnedFavoriteAndUnreadSkipAgeHide() {
        val old = agent("old", status = "FINISHED", updatedAt = "2026-09-01T00:00:00Z")
        val pinned = arrange(
            listOf(old),
            hideDays = 7,
            pinnedAt = mapOf("old" to 1L),
        )
        assertEquals(listOf("old"), pinned.pinned.map { it.id })
        assertEquals(0, pinned.hiddenFinished)

        val starred = arrange(
            listOf(old),
            hideDays = 7,
            favoriteIds = setOf("old"),
        )
        assertEquals(listOf("old"), starred.rest.map { it.id })
        assertEquals(0, starred.hiddenFinished)

        val unread = arrangeCloudAgents(
            agents = listOf(old),
            git = emptyMap(),
            pinnedAt = emptyMap(),
            favoriteIds = emptySet(),
            approvalIds = emptySet(),
            filter = CloudFilter.All,
            hideFinishedDays = 7,
            revealFinished = false,
            groupByRepo = false,
            nowMillis = now,
            unreadAgentIds = setOf("old"),
        )
        assertEquals(listOf("old"), unread.rest.map { it.id })
        assertEquals(0, unread.hiddenFinished)
    }

    @Test
    fun searchBypassesAgeFilter() {
        val old = agent("old", status = "FINISHED", updatedAt = "2026-09-01T00:00:00Z")
        val arranged = arrangeCloudAgents(
            agents = listOf(old),
            git = emptyMap(),
            pinnedAt = emptyMap(),
            favoriteIds = emptySet(),
            approvalIds = emptySet(),
            filter = CloudFilter.All,
            hideFinishedDays = 7,
            revealFinished = false,
            groupByRepo = true,
            nowMillis = now,
            applyAgeFilter = false,
        )
        assertEquals(listOf("old"), arranged.groups.single().agents.map { it.id })
        assertEquals(0, arranged.hiddenFinished)
    }

    @Test
    fun activityBoundaryUsesStrictlyOlderThanWindow() {
        val cutoff = agent("edge", status = "FINISHED", updatedAt = "2026-10-03T12:00:00Z")
        val hidden = agent("past", status = "FINISHED", updatedAt = "2026-10-02T12:00:00Z")
        val arranged = arrange(listOf(cutoff, hidden), hideDays = 7)
        assertEquals(listOf("edge"), arranged.rest.map { it.id })
        assertEquals(1, arranged.hiddenFinished)
    }

    private fun agent(
        id: String,
        status: String = "FINISHED",
        updatedAt: String = "2026-10-10T00:00:00Z",
    ): AgentSummary = AgentSummary(id = id, name = id, status = status, updatedAt = updatedAt)
}

private object InstantMillis {
    fun of(iso: String): Long = java.time.Instant.parse(iso).toEpochMilli()
}
