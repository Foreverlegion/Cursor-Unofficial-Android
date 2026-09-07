package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {
    private val device = BugReport.Device(
        versionName = "1.0.16",
        versionCode = 116,
        sdk = 34,
        release = "14",
        manufacturer = "Google",
        model = "Pixel 8",
    )

    @Test
    fun titleIsSingleLineAndCapped() {
        val title = BugReport.sanitizeTitle("crash\non launch" + "x".repeat(200))
        assertFalse(title.contains('\n'))
        assertEquals(BugReport.MAX_TITLE, title.length)
    }

    @Test
    fun bodyRedactsGithubTokens() {
        val body = BugReport.sanitizeBody("key ghp_abcdefghijklmnopqrstuvwxyz123456 and github_pat_abcdefghijklmnopqrstuvwxyz")
        assertFalse(body.contains("ghp_"))
        assertFalse(body.contains("github_pat_"))
        assertTrue(body.contains("[redacted]"))
    }

    @Test
    fun composeAddsDeviceAndNoIdentity() {
        val body = BugReport.composeBody("buttons overlap", device)
        assertTrue(body.contains("buttons overlap"))
        assertTrue(body.contains("App 1.0.16 (116)"))
        assertTrue(body.contains("Android 14 (SDK 34)"))
        assertTrue(body.contains("Google Pixel 8"))
        assertTrue(body.contains("No account identity attached"))
    }

    @Test
    fun newIssueUrlAssignsOwner() {
        val url = BugReport.newIssueUrl("Crash", "it died")
        assertTrue(url.startsWith("https://github.com/Foreverlegion/Cursor-Unofficial-Android/issues/new"))
        assertTrue(url.contains("assignees=Foreverlegion"))
        assertTrue(url.contains("labels=bug"))
        assertTrue(url.contains("title=Crash"))
    }
}
