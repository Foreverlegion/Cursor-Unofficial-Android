package com.cursorandroid.app.data.repo

import com.cursorandroid.app.ui.status.PrCheckChipKind
import com.cursorandroid.app.ui.status.PrCheckRunView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubPrChecksTest {
    @Test
    fun parseGithubPullUrls() {
        val ref = GithubPrChecks.parsePullUrl("https://github.com/Foreverlegion/Cursor-Unofficial-Android/pull/24")
        assertEquals("Foreverlegion", ref?.owner)
        assertEquals("Cursor-Unofficial-Android", ref?.repo)
        assertEquals(24, ref?.number)
        assertNull(GithubPrChecks.parsePullUrl("https://gitlab.com/acme/app/-/merge_requests/1"))
    }

    @Test
    fun enrichTestDetailsLeavesNonTestRunsAlone() {
        val ref = GithubPullRef("acme", "app", 1)
        val runs = listOf(
            PrCheckRunView("preview-apk", "completed", "failure", id = 99L),
        )
        val out = GithubPrChecks.enrichTestDetails("token", "https://api.github.com", ref, runs)
        assertEquals(runs, out)
    }

    @Test
    fun enrichTestDetailsSkipsWhenAlreadyPresent() {
        val ref = GithubPullRef("acme", "app", 1)
        val runs = listOf(
            PrCheckRunView(
                name = "unit-tests",
                status = "completed",
                conclusion = "failure",
                id = 0L,
                testDetail = "AgentListLogicTest.ageFilter",
            ),
        )
        val out = GithubPrChecks.enrichTestDetails("token", "https://api.github.com", ref, runs)
        assertEquals("AgentListLogicTest.ageFilter", out.single().testDetail)
    }
}
