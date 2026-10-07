package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {
    @Test
    fun titleIsSingleLineAndCapped() {
        val title = BugReport.sanitizeTitle("crash\non launch" + "x".repeat(200))
        assertFalse(title.contains('\n'))
        assertEquals(BugReport.MAX_TITLE, title.length)
    }

    @Test
    fun bodyRedactsSecretsAndEmail() {
        val body = BugReport.sanitizeBody(
            "key ghp_abcdefghijklmnopqrstuvwxyz123456 and github_pat_abcdefghijklmnopqrstuvwxyz " +
                "mail me at person@example.com key_abcdefghijklmnopqrstuvwxyz",
        )
        assertFalse(body.contains("ghp_"))
        assertFalse(body.contains("github_pat_"))
        assertFalse(body.contains("person@example.com"))
        assertFalse(body.contains("key_abcdefghijklmnopqrstuvwxyz"))
        assertTrue(body.contains("[redacted]"))
    }

    @Test
    fun composeOmitsIdentity() {
        val body = BugReport.composeBody(
            userText = "buttons overlap",
            versionName = "1.0.24",
            versionCode = 124,
            androidRelease = "14",
            sdk = 34,
        )
        assertTrue(body.contains("buttons overlap"))
        assertTrue(body.contains("App 1.0.24 (124)"))
        assertTrue(body.contains("Android 14 (SDK 34)"))
        assertTrue(body.contains("No name, email, API key, or device is attached."))
        assertFalse(body.contains("Pixel"))
        assertFalse(body.contains("@"))
    }

    @Test
    fun bannedInstallDoesNotFile() {
        val ledger = MemoryLedger(banned = true)
        val error = runCatching {
            BugReport.submit(
                title = "Crash",
                userText = "it died",
                versionName = "1.0.24",
                versionCode = 124,
                androidRelease = "14",
                sdk = 34,
                ledger = ledger,
            )
        }.exceptionOrNull()
        assertTrue(error is BugReport.Blocked)
        assertEquals(FeedbackPolicy.BLOCKED, error?.message)
        assertTrue(ledger.numbers().isEmpty())
    }

    @Test
    fun operatorIsTheOwnerEmailOnly() {
        assertTrue(FeedbackPolicy.isOperator("Foreverlegion@gmail.com"))
        assertFalse(FeedbackPolicy.isOperator("someone@example.com"))
        assertFalse(FeedbackPolicy.isOperator(null))
    }

    @Test
    fun serverBanBlocksTheNextSubmit() {
        assertFalse(FeedbackPolicy.blocksSubmit(localBanned = false, serverBanned = false))
        assertTrue(FeedbackPolicy.blocksSubmit(localBanned = true, serverBanned = false))
        assertTrue(FeedbackPolicy.blocksSubmit(localBanned = false, serverBanned = true))
    }

    private class MemoryLedger(var banned: Boolean) : ReportLedger {
        private val filed = mutableListOf<Int>()
        override fun banned(): Boolean = banned
        override fun ban() {
            banned = true
        }
        override fun numbers(): List<Int> = filed
        override fun remember(number: Int, title: String) {
            filed += number
        }
    }
}
