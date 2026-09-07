package com.cursorandroid.app.data.repo

object ReleaseNotes {
    const val MAX_CHARS = 8_000

    fun shouldOffer(remote: AppUpdate.Remote, installedCode: Long, skippedCode: Long): Boolean {
        return remote.versionCode > installedCode &&
            remote.versionCode != skippedCode &&
            !remote.apkUrl.isNullOrBlank()
    }

    fun display(raw: String?, versionName: String): String {
        val text = strip(raw.orEmpty()).trim()
        if (text.isNotEmpty()) return text.take(MAX_CHARS)
        val name = versionName.trim().ifBlank { "this release" }
        return "Version $name is available."
    }

    internal fun strip(raw: String): String {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        text = HTML_COMMENT.replace(text, "")
        text = FENCE.replace(text) { match -> match.groupValues[1].trim() }
        text = LINK.replace(text) { match -> match.groupValues[1] }
        text = IMAGE.replace(text) { match -> match.groupValues[1] }
        text = HEADING.replace(text, "")
        text = BOLD.replace(text) { match -> match.groupValues[1] }
        text = ITALIC.replace(text) { match -> match.groupValues[1] }
        text = HTML_TAG.replace(text, "")
        return text.lines().joinToString("\n") { it.trimEnd() }.trim()
    }

    private val HTML_COMMENT = Regex("<!--[\\s\\S]*?-->")
    private val FENCE = Regex("```(?:[a-zA-Z0-9_-]+)?\\s*([\\s\\S]*?)```")
    private val LINK = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
    private val IMAGE = Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)")
    private val HEADING = Regex("(?m)^#{1,6}\\s+")
    private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")
    private val ITALIC = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")
    private val HTML_TAG = Regex("</?[a-zA-Z][^>]*>")
}
