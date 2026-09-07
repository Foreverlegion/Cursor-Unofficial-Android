package com.cursorandroid.app.data.repo

import java.net.URLEncoder

object BugReport {
    const val MAX_TITLE = 80
    const val MAX_BODY = 4_000
    const val MAX_URL_BODY = 1_500

    data class Device(
        val versionName: String,
        val versionCode: Long,
        val sdk: Int,
        val release: String,
        val manufacturer: String,
        val model: String,
    )

    fun sanitizeTitle(raw: String): String {
        return clean(raw).replace('\n', ' ').replace('\r', ' ').trim().take(MAX_TITLE)
    }

    fun sanitizeBody(raw: String): String {
        return redact(clean(raw)).trim().take(MAX_BODY)
    }

    fun composeBody(userText: String, device: Device): String {
        val report = sanitizeBody(userText)
        val tech = buildString {
            append("App ").append(device.versionName.ifBlank { "?" })
            append(" (").append(device.versionCode).append(")\n")
            append("Android ").append(device.release.ifBlank { "?" })
            append(" (SDK ").append(device.sdk).append(")\n")
            append(device.manufacturer.trim()).append(' ').append(device.model.trim())
        }.trim()
        return buildString {
            if (report.isNotEmpty()) {
                append(report)
                append("\n\n")
            }
            append("---\n")
            append(tech)
            append("\nReported from the Android app. No account identity attached.")
        }
    }

    fun newIssueUrl(title: String, body: String): String {
        val heading = sanitizeTitle(title).ifBlank { "Android bug" }
        val text = body.take(MAX_URL_BODY)
        return "https://github.com/${GithubAppIssues.REPO}/issues/new" +
            "?assignees=${enc(GithubAppIssues.ASSIGNEE)}" +
            "&labels=bug" +
            "&title=${enc(heading)}" +
            "&body=${enc(text)}"
    }

    fun submit(title: String, userText: String, device: Device, userToken: String?): String {
        val heading = sanitizeTitle(title).ifBlank { "Android bug" }
        val body = composeBody(userText, device)
        val token = GithubAppIssues.bakedToken() ?: userToken?.trim()?.takeIf { it.isNotEmpty() }
        if (token.isNullOrEmpty()) {
            return newIssueUrl(heading, body)
        }
        val created = runCatching {
            GithubAppIssues.createIssue(token, heading, body, labels = listOf("bug"))
        }.getOrElse {
            return newIssueUrl(heading, body)
        }
        return SafeLinks.githubHttps(created.html_url)?.toString() ?: newIssueUrl(heading, body)
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

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private val SECRET_PREFIXES = listOf(
        Regex("ghp_[A-Za-z0-9_]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("gho_[A-Za-z0-9_]{20,}"),
        Regex("ghu_[A-Za-z0-9_]{20,}"),
        Regex("ghs_[A-Za-z0-9_]{20,}"),
        Regex("ghr_[A-Za-z0-9_]{20,}"),
    )
}
