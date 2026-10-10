package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

private class FakeForgeTransport(private val reply: (ForgeCall) -> ForgeResponse) : ForgeTransport {
    val calls = mutableListOf<ForgeCall>()

    override fun execute(call: ForgeCall): ForgeResponse {
        synchronized(calls) { calls += call }
        return reply(call)
    }
}

private fun githubRows(from: Int, count: Int): String = (from until from + count).joinToString(",", "[", "]") {
    """{"full_name":"acme/r$it","clone_url":"https://github.com/acme/r$it.git","default_branch":"main"}"""
}

private fun gitlabRows(from: Int, count: Int): String = (from until from + count).joinToString(",", "[", "]") {
    """{"path_with_namespace":"grp/p$it","http_url_to_repo":"https://gitlab.com/grp/p$it.git","default_branch":"main"}"""
}

private fun pageOf(url: String): Int = Regex("[?&]page=(\\d+)").find(url)!!.groupValues[1].toInt()

class ForgeRepoSyncTest {
    private val github = publicGithubForge("ghp_secret", id = "gh")
    private val gitlab = ForgeConnection(id = "gl", provider = ForgeKind.GITLAB.id, token = "glpat-secret")
    private val tmp = Files.createTempDirectory("forge-sync").toFile()
    private val scope = CoroutineScope(Job())

    @After
    fun cleanup() {
        tmp.deleteRecursively()
    }

    @Test
    fun githubSyncFollowsLinkHeaderAcrossPagesWithAffiliationAndToken() {
        val transport = FakeForgeTransport { call ->
            when (pageOf(call.url)) {
                1 -> ForgeResponse(
                    200,
                    githubRows(0, 100),
                    mapOf("link" to "<https://api.github.com/user/repos?page=2>; rel=\"next\", <x>; rel=\"last\""),
                )
                else -> ForgeResponse(200, githubRows(100, 3), mapOf("link" to "<x>; rel=\"prev\""))
            }
        }
        val result = fetchForgeRepos(github, transport)

        assertNull(result.error)
        assertEquals(103, result.repos.size)
        assertEquals("https://github.com/acme/r0.git", result.repos.first().url)
        assertEquals("github", result.repos.first().provider)
        assertEquals(2, transport.calls.size)
        val first = transport.calls[0]
        assertTrue(first.url.startsWith("https://api.github.com/user/repos?"))
        assertTrue(first.url.contains("affiliation=owner,collaborator,organization_member"))
        assertTrue(first.url.contains("per_page=100"))
        assertTrue(first.url.contains("page=1"))
        assertEquals("Bearer ghp_secret", first.headers["Authorization"])
        assertTrue(transport.calls[1].url.contains("page=2"))
    }

    @Test
    fun githubFullPageWithoutLinkHeaderAsksForTheNextPageAndStopsOnEmpty() {
        val transport = FakeForgeTransport { call ->
            if (pageOf(call.url) == 1) ForgeResponse(200, githubRows(0, 100)) else ForgeResponse(200, "[]")
        }
        val result = fetchForgeRepos(github, transport)

        assertEquals(100, result.repos.size)
        assertEquals(2, transport.calls.size)
        assertNull(result.error)
    }

    @Test
    fun gitlabSyncUsesPrivateTokenMembershipAndNextPageHeader() {
        val transport = FakeForgeTransport { call ->
            when (pageOf(call.url)) {
                1 -> ForgeResponse(200, gitlabRows(0, 100), mapOf("x-next-page" to "2"))
                2 -> ForgeResponse(200, gitlabRows(100, 100), mapOf("x-next-page" to "3"))
                else -> ForgeResponse(200, gitlabRows(200, 7), mapOf("x-next-page" to ""))
            }
        }
        val result = fetchForgeRepos(gitlab, transport)

        assertNull(result.error)
        assertEquals(207, result.repos.size)
        assertEquals("gitlab", result.repos.first().provider)
        assertEquals("https://gitlab.com/grp/p0.git", result.repos.first().url)
        assertEquals(3, transport.calls.size)
        transport.calls.forEach { call ->
            assertTrue(call.url.startsWith("https://gitlab.com/api/v4/projects?membership=true"))
            assertTrue(call.url.contains("per_page=100"))
            assertEquals("glpat-secret", call.headers["PRIVATE-TOKEN"])
            assertNull(call.headers["Authorization"])
        }
        assertEquals(listOf(1, 2, 3), transport.calls.map { pageOf(it.url) })
    }

    @Test
    fun selfHostedGitlabSyncsFromItsOwnHost() {
        val self = ForgeConnection(
            id = "s", provider = ForgeKind.GITLAB_SELF.id, token = "t", baseUrl = "https://git.corp.example",
        )
        val transport = FakeForgeTransport { ForgeResponse(200, "[]", mapOf("x-next-page" to "")) }
        fetchForgeRepos(self, transport)
        assertTrue(transport.calls.single().url.startsWith("https://git.corp.example/api/v4/projects"))
    }

    @Test
    fun unauthorizedIsReportedAsAnAuthErrorWithoutLeakingTheToken() {
        val transport = FakeForgeTransport { ForgeResponse(401, """{"message":"Bad credentials"}""") }

        val gh = fetchForgeRepos(github, transport)
        assertTrue(gh.authFailed)
        assertTrue(gh.error!!.contains("rejected the token (401)"))
        assertFalse(gh.error!!.contains("ghp_secret"))
        assertTrue(gh.repos.isEmpty())

        val gl = fetchForgeRepos(gitlab, transport)
        assertTrue(gl.authFailed)
        assertTrue(gl.error!!.contains("GitLab.com rejected the token (401)"))
    }

    @Test
    fun forbiddenIsAnAuthErrorButRateLimitingIsNot() {
        val denied = fetchForgeRepos(github, FakeForgeTransport { ForgeResponse(403, "{}") })
        assertTrue(denied.authFailed)
        assertTrue(denied.error!!.contains("denied access (403)"))

        val limited = fetchForgeRepos(
            github,
            FakeForgeTransport { ForgeResponse(403, "{}", mapOf("x-ratelimit-remaining" to "0")) },
        )
        assertFalse(limited.authFailed)
        assertTrue(limited.error!!.contains("rate limit"))

        val sso = fetchForgeRepos(
            github,
            FakeForgeTransport { ForgeResponse(403, "{}", mapOf("x-github-sso" to "required; url=https://x")) },
        )
        assertTrue(sso.error!!.contains("SSO"))
    }

    @Test
    fun networkFailureAndServerErrorsAreNamed() {
        assertEquals("Couldn't reach GitHub.", fetchForgeRepos(github, FakeForgeTransport { ForgeResponse(0) }).error)
        assertEquals("GitHub returned 502.", fetchForgeRepos(github, FakeForgeTransport { ForgeResponse(502) }).error)
    }

    @Test
    fun failureOnALaterPageKeepsWhatWasFetchedAndReportsTheError() {
        val transport = FakeForgeTransport { call ->
            if (pageOf(call.url) == 1) {
                ForgeResponse(200, githubRows(0, 100), mapOf("link" to "<x>; rel=\"next\""))
            } else {
                ForgeResponse(403, "{}")
            }
        }
        val result = fetchForgeRepos(github, transport)

        assertEquals(100, result.repos.size)
        assertTrue(result.error!!.contains("403"))
    }

    @Test
    fun pagingStopsAtTheCapAndFlagsTruncation() {
        val transport = FakeForgeTransport { ForgeResponse(200, githubRows(0, 100), mapOf("link" to "<x>; rel=\"next\"")) }
        val result = fetchForgeRepos(github, transport, maxPages = 3)

        assertEquals(3, transport.calls.size)
        assertTrue(result.truncated)
    }

    @Test
    fun forgeWithoutTokenOrListingSupportMakesNoCalls() {
        val transport = FakeForgeTransport { ForgeResponse(200, "[]") }
        assertEquals("Add a token.", fetchForgeRepos(github.copy(token = ""), transport).error)
        val azure = ForgeConnection(id = "az", provider = ForgeKind.AZURE.id, token = "t", username = "org")
        assertTrue(fetchForgeRepos(azure, transport).error!!.contains("isn't available"))
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun syncerCachesReposPerForgeAndSurvivesARestart() = runBlocking {
        val transport = FakeForgeTransport { call ->
            if (call.url.contains("gitlab.com")) {
                ForgeResponse(200, gitlabRows(0, 2), mapOf("x-next-page" to ""))
            } else {
                ForgeResponse(200, githubRows(0, 3))
            }
        }
        val syncer = syncer(listOf(github, gitlab), transport, clock = { 1_000 })
        syncer.syncAll()

        val state = syncer.state.value
        assertEquals(3, state.reposByForge["gh"]!!.size)
        assertEquals(2, state.reposByForge["gl"]!!.size)
        assertEquals(5, state.repos().size)
        assertEquals(3, state.statuses["gh"]!!.repoCount)
        assertEquals(1_000L, state.statuses["gh"]!!.syncedAt)
        assertTrue(state.running.isEmpty())

        val restarted = syncer(listOf(github, gitlab), transport)
        restarted.load()
        assertEquals(5, restarted.state.value.repos().size)
        assertEquals(2, restarted.state.value.statuses.size)
    }

    @Test
    fun failedSyncKeepsThePreviousListAndRecordsTheError() = runBlocking {
        var code = 200
        val transport = FakeForgeTransport { if (code == 200) ForgeResponse(200, githubRows(0, 4)) else ForgeResponse(401) }
        var time = 1_000L
        val syncer = syncer(listOf(github), transport, clock = { time })
        syncer.syncAll()
        code = 401
        time = 9_000L
        syncer.sync(github)

        val status = syncer.state.value.statuses["gh"]!!
        assertEquals(4, syncer.state.value.reposByForge["gh"]!!.size)
        assertEquals(1_000L, status.syncedAt)
        assertEquals(9_000L, status.attemptedAt)
        assertTrue(status.authFailed)
        assertTrue(status.error!!.contains("401"))
        assertTrue(status.syncLine(10_000L).contains("4 repos"))
        assertTrue(status.syncLine(10_000L).contains("rejected the token"))
    }

    @Test
    fun dueSyncSkipsFreshForgesAndRetriesFailedOrChangedOnes() = runBlocking {
        var time = 0L
        val transport = FakeForgeTransport { ForgeResponse(200, githubRows(0, 1)) }
        var forges = listOf(github)
        val syncer = ForgeSyncer(ForgeSyncStore(tmp), { forges }, scope, { transport }, { time })
        syncer.syncAll()
        assertEquals(1, transport.calls.size)

        time = 60_000
        syncer.syncAll(maxAgeMs = FORGE_SYNC_START_MS, force = false)
        assertEquals("fresh forge is not synced again", 1, transport.calls.size)

        time = FORGE_SYNC_START_MS + 1
        syncer.syncAll(maxAgeMs = FORGE_SYNC_START_MS, force = false)
        assertEquals("stale forge is synced", 2, transport.calls.size)

        forges = listOf(publicGithubForge("ghp_new", id = "gh"))
        syncer.syncAll(maxAgeMs = FORGE_SYNC_BACKGROUND_MS, force = false)
        assertEquals("a changed token is synced even when fresh", 3, transport.calls.size)

        syncer.syncAll(maxAgeMs = FORGE_SYNC_BACKGROUND_MS, force = false)
        assertEquals(3, transport.calls.size)
    }

    @Test
    fun backgroundWindowIsSixHours() {
        assertEquals(6 * 60 * 60 * 1000L, FORGE_SYNC_BACKGROUND_MS)
    }

    @Test
    fun removedForgeIsPrunedAndForgeWithoutTokenIsSkipped() = runBlocking {
        val transport = FakeForgeTransport { ForgeResponse(200, "[]") }
        var forges = listOf(github, gitlab)
        val syncer = ForgeSyncer(ForgeSyncStore(tmp), { forges }, scope, { transport })
        syncer.syncAll()
        assertEquals(setOf("gh", "gl"), syncer.state.value.statuses.keys)

        forges = listOf(github, gitlab.copy(token = ""))
        val before = transport.calls.size
        syncer.syncAll()
        assertEquals(setOf("gh"), syncer.state.value.statuses.keys)
        assertEquals(before + 1, transport.calls.size)
        assertNull(ForgeSyncStore(tmp).repos("gl"))
    }

    @Test
    fun demoModeAndUnsupportedForgesDoNotSync() = runBlocking {
        val transport = FakeForgeTransport { ForgeResponse(200, "[]") }
        val demo = ForgeSyncer(ForgeSyncStore(tmp), { listOf(github) }, scope, { transport }, enabled = { false })
        demo.syncAll()
        assertNull(demo.sync(github))
        assertTrue(transport.calls.isEmpty())

        val azure = ForgeConnection(id = "az", provider = ForgeKind.AZURE.id, token = "t", username = "org")
        val live = syncer(listOf(azure), transport)
        live.syncAll()
        assertTrue(transport.calls.isEmpty())
        assertTrue(live.state.value.statuses.isEmpty())
    }

    @Test
    fun syncLineReadsNaturally() {
        val now = 10 * 60 * 60 * 1000L
        assertEquals("Not synced yet", (null as ForgeSyncStatus?).syncLine(now))
        assertEquals(
            "Last synced 5 min ago · 342 repos",
            ForgeSyncStatus("a", attemptedAt = now, syncedAt = now - 5 * 60_000, repoCount = 342).syncLine(now),
        )
        assertEquals(
            "Last synced 2 h ago · 1 repo · GitHub returned 502.",
            ForgeSyncStatus("a", now, now - 2 * 3_600_000, 1, error = "GitHub returned 502.").syncLine(now),
        )
        assertEquals(
            "Not synced yet · GitHub rejected the token (401). Check it in Settings > Forges.",
            ForgeSyncStatus("a", now, error = "GitHub rejected the token (401). Check it in Settings > Forges.")
                .syncLine(now),
        )
    }

    @Test
    fun reposAreGroupedByTheForgeThatListedThem() {
        val ghRepo = RepositoryItem("https://github.com/acme/a.git", "github")
        val glRepo = RepositoryItem("https://gitlab.com/grp/b.git", "gitlab")
        val cursorOnly = RepositoryItem("https://origin.cursor.com/x/y", "origin")
        val groups = groupReposByForge(
            repos = listOf(cursorOnly, glRepo, ghRepo),
            forges = listOf(github, gitlab),
            byForge = mapOf("gh" to listOf(ghRepo), "gl" to listOf(RepositoryItem("https://gitlab.com/grp/b", "gitlab"))),
        )

        assertEquals(listOf("GitHub", "GitLab.com", "Cursor"), groups.map { it.name })
        assertEquals(listOf(ghRepo), groups[0].repos)
        assertEquals(listOf(glRepo), groups[1].repos)
        assertEquals(listOf(cursorOnly), groups[2].repos)
    }

    @Test
    fun fingerprintChangesWithTheTokenButDoesNotContainIt() {
        val a = forgeFingerprint(github)
        assertNotNull(a)
        assertFalse(a.contains("ghp"))
        assertEquals(a, forgeFingerprint(github))
        assertTrue(a != forgeFingerprint(publicGithubForge("ghp_other", id = "gh")))
    }

    private fun syncer(
        forges: List<ForgeConnection>,
        transport: ForgeTransport,
        clock: () -> Long = { System.currentTimeMillis() },
    ) = ForgeSyncer(ForgeSyncStore(tmp), { forges }, scope, { transport }, clock)
}
