package com.cursorandroid.app.data.repo

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
}
