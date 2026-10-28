package com.cursorandroid.app.ui.inbox

/**
 * Parses the unified inbox search box. PR-shaped input drives the same server-side [apiPrUrl]
 * filter the old PR field used. Everything else is a local text needle.
 */
data class ParsedInboxSearch(
    val raw: String,
    val apiPrUrl: String?,
    val textNeedle: String,
) {
    val hasPrFilter: Boolean get() = !apiPrUrl.isNullOrBlank()
    val hasActiveFilter: Boolean get() = raw.isNotBlank()
}

private val HASH_PR = Regex("^#(\\d+)$")
private val DIGITS_ONLY = Regex("^\\d+$")

fun parseInboxSearchQuery(raw: String): ParsedInboxSearch {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return ParsedInboxSearch(raw = "", apiPrUrl = null, textNeedle = "")
    }
    HASH_PR.matchEntire(trimmed)?.let { match ->
        val n = match.groupValues[1]
        return ParsedInboxSearch(raw = trimmed, apiPrUrl = "#$n", textNeedle = "")
    }
    val pr = normalizePrUrl(trimmed)
    if (pr != null) {
        return ParsedInboxSearch(raw = trimmed, apiPrUrl = pr, textNeedle = "")
    }
    return ParsedInboxSearch(raw = trimmed, apiPrUrl = null, textNeedle = trimmed)
}

fun normalizePrUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (HASH_PR.matches(trimmed)) return trimmed
    if (DIGITS_ONLY.matches(trimmed)) return null
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val lower = candidate.lowercase()
    val looksPr = lower.contains("/pull/") ||
        lower.contains("/merge_requests/") ||
        lower.contains("/-/merge_requests/")
    if (!looksPr) return null
    return candidate
}

fun agentMatchesPrFilter(prUrl: String?, filter: String): Boolean {
    val needle = filter.trim()
    if (needle.isEmpty()) return true
    if (needle.startsWith("#")) {
        val n = needle.removePrefix("#")
        if (n.isEmpty()) return true
        return prUrl?.contains("/pull/$n") == true ||
            prUrl?.contains("/merge_requests/$n") == true ||
            prUrl?.endsWith("/$n") == true
    }
    return prUrl.equals(needle, ignoreCase = true) ||
        prUrl?.contains(needle, ignoreCase = true) == true
}
