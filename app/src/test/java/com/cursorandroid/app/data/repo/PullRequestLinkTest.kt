package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PullRequestLinkTest {
    @Test
    fun acceptsLonePullRequestUrls() {
        assertEquals(
            "https://github.com/acme/app/pull/12",
            SafeLinks.pullRequestUrl("  https://github.com/acme/app/pull/12/files?diff=split#x "),
        )
        assertEquals(
            "https://gitlab.com/acme/group/app/-/merge_requests/7",
            SafeLinks.pullRequestUrl("https://gitlab.com/acme/group/app/-/merge_requests/7"),
        )
    }

    @Test
    fun rejectsEverythingElse() {
        assertNull(SafeLinks.pullRequestUrl("look at https://github.com/acme/app/pull/12"))
        assertNull(SafeLinks.pullRequestUrl("http://github.com/acme/app/pull/12"))
        assertNull(SafeLinks.pullRequestUrl("https://github.com/acme/app/issues/12"))
        assertNull(SafeLinks.pullRequestUrl("https://user:pw@github.com/acme/app/pull/12"))
        assertNull(SafeLinks.pullRequestUrl(null))
    }
}
