package com.cursorandroid.app.ui.inbox

/**
 * Parses the inbox search field. Full pull/merge-request URLs reload the agent list with `prUrl`.
 * A lone `#123` or `123` filters client-side on each chat's stored PR link. Anything else is plain text search.
 */
data class InboxSearchQuery(
    val raw: String,
    val prUrlForApi: String?,
    val prNumber: Int?,
    val textNeedle: String,
) {
    val hasPrFilter: Boolean get() = !prUrlForApi.isNullOrBlank() || prNumber != null
    val isEmpty: Boolean get() = raw.isBlank()
}

object InboxSearch {
    private val githubPull = Regex("""(?i)(?:https?://)?(?:www\.)?github\.com/[^/\s]+/[^/\s]+/pull/\d+""")
    private val gitlabMr = Regex("""(?i)(?:https?://)?[^/\s]+/[^/\s]+/[^/\s]+/-/merge_requests/\d+""")
    private val gitlabMrShort = Regex("""(?i)(?:https?://)?[^/\s]+/[^/\s]+/merge_requests/\d+""")
    private val originPull = Regex("""(?i)(?:https?://)?(?:www\.)?origin\.cursor\.com/[^/\s]+/[^/\s]+/pull/\d+""")
    private val prNumberOnly = Regex("""^#(\d+)$""")

    fun parse(raw: String): InboxSearchQuery {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return InboxSearchQuery("", null, null, "")
        }
        normalizePrUrl(trimmed)?.let { url ->
            return InboxSearchQuery(trimmed, url, null, "")
        }
        prNumberOnly.matchEntire(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
            return InboxSearchQuery(trimmed, null, num, "")
        }
        return InboxSearchQuery(trimmed, null, null, trimmed)
    }

    fun normalizePrUrl(input: String): String? {
        val candidate = input.trim().removeSuffix("/")
        if (candidate.isEmpty()) return null
        val withScheme = when {
            candidate.startsWith("http://", ignoreCase = true) ||
                candidate.startsWith("https://", ignoreCase = true) -> candidate
            candidate.contains("github.com/", ignoreCase = true) ||
                candidate.contains("merge_requests", ignoreCase = true) ||
                candidate.contains("origin.cursor.com/", ignoreCase = true) -> "https://$candidate"
            else -> candidate
        }
        return when {
            githubPull.containsMatchIn(withScheme) -> withScheme
            gitlabMr.containsMatchIn(withScheme) -> withScheme
            gitlabMrShort.containsMatchIn(withScheme) -> withScheme
            originPull.containsMatchIn(withScheme) -> withScheme
            else -> null
        }
    }

    fun matchesPrNumber(prUrl: String?, number: Int): Boolean {
        if (prUrl.isNullOrBlank()) return false
        val path = prUrl.trim().lowercase()
        return path.contains("/pull/$number") ||
            path.contains("/merge_requests/$number") ||
            path.endsWith("/$number")
    }
}
