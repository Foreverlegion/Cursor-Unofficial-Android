package com.cursorandroid.app.data.repo

object BugReport {
    const val MAX_TITLE = 80
    const val MAX_BODY = 4_000

    class Blocked(message: String) : IllegalStateException(message)

    data class Filed(val number: Int)

    fun sanitizeTitle(raw: String): String {
        return redact(clean(raw)).replace('\n', ' ').replace('\r', ' ').trim().take(MAX_TITLE)
    }

    fun sanitizeBody(raw: String): String {
        return redact(clean(raw)).trim().take(MAX_BODY)
    }

    fun composeBody(
        userText: String,
        versionName: String,
        versionCode: Long,
        androidRelease: String,
        sdk: Int,
        kind: FeedbackPolicy.Kind = FeedbackPolicy.Kind.BUG,
    ): String {
        val report = sanitizeBody(userText)
        return buildString {
            if (report.isNotEmpty()) {
                append(report)
                append("\n\n")
            }
            append("---\n")
            append(if (kind == FeedbackPolicy.Kind.FEATURE) "Type: feature request\n" else "Type: bug report\n")
            append("App ").append(versionName.ifBlank { "?" })
            append(" (").append(versionCode).append(")\n")
            append("Android ").append(androidRelease.ifBlank { "?" })
            append(" (SDK ").append(sdk).append(")\n")
            append("Anonymous report from the Android app. No name, email, API key, or device is attached.\n")
            append("Reply on this issue to answer in the app.")
        }
    }

    /**
     * Files with the app token only. A personal GitHub token would put the reporter's login on the issue.
     * Returns after the issue exists. Does not open a browser.
     */
    fun submit(
        title: String,
        userText: String,
        versionName: String,
        versionCode: Long,
        androidRelease: String,
        sdk: Int,
        ledger: ReportLedger,
        kind: FeedbackPolicy.Kind = FeedbackPolicy.Kind.BUG,
    ): Filed {
        if (FeedbackPolicy.blocksSubmit(ledger.banned(), serverBanned = false)) {
            ledger.ban()
            throw Blocked(FeedbackPolicy.BLOCKED)
        }
        val token = GithubAppIssues.bakedToken()
            ?: throw Blocked("Feedback is unavailable right now.")
        val serverBanned = ledger.numbers().any { number ->
            GithubAppIssues.getIssue(token, number)?.let { issue ->
                issue.labels.any { FeedbackPolicy.isBannedLabel(it.name) }
            } == true
        }
        if (FeedbackPolicy.blocksSubmit(localBanned = false, serverBanned = serverBanned)) {
            ledger.ban()
            throw Blocked(FeedbackPolicy.BLOCKED)
        }
        val fallback = if (kind == FeedbackPolicy.Kind.FEATURE) "Feature request" else "Android bug"
        val heading = sanitizeTitle(title).ifBlank { fallback }
        val body = composeBody(userText, versionName, versionCode, androidRelease, sdk, kind)
        val extra = FeedbackPolicy.kindLabel(kind)
        GithubAppIssues.ensureLabel(token, FeedbackPolicy.LABEL_FEEDBACK, "1d76db")
        GithubAppIssues.ensureLabel(
            token,
            extra,
            if (kind == FeedbackPolicy.Kind.FEATURE) "a2eeef" else "d73a4a",
        )
        val created = GithubAppIssues.createIssue(
            token,
            heading,
            body,
            labels = listOf(FeedbackPolicy.LABEL_FEEDBACK, extra),
        )
        val number = created.number ?: throw Blocked("GitHub did not accept the report.")
        ledger.remember(number, heading)
        return Filed(number)
    }

    internal fun redact(raw: String): String {
        var text = raw
        SECRET_PREFIXES.forEach { pattern ->
            text = pattern.replace(text, "[redacted]")
        }
        return text
    }

    private fun clean(raw: String): String {
        return buildString(raw.length) {
            raw.forEach { ch ->
                if (ch == '\n' || ch == '\t' || ch == '\r' || ch >= ' ') append(ch)
            }
        }
    }

    private val SECRET_PREFIXES = listOf(
        Regex("ghp_[A-Za-z0-9_]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("gho_[A-Za-z0-9_]{20,}"),
        Regex("ghu_[A-Za-z0-9_]{20,}"),
        Regex("ghs_[A-Za-z0-9_]{20,}"),
        Regex("ghr_[A-Za-z0-9_]{20,}"),
        Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]{8,}"),
        Regex("(?i)\\bkey_[A-Za-z0-9_\\-]{12,}"),
        Regex("(?i)\\bcrsr_[A-Za-z0-9_\\-]{12,}"),
        Regex("(?i)\\bsk-[A-Za-z0-9_\\-]{12,}"),
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
    )
}
