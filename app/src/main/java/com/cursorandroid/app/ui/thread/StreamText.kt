package com.cursorandroid.app.ui.thread

/**
 * One run's assistant and thinking text.
 *
 * Cloud Agents SSE `assistant` and `thinking` events carry `{ text }`. That field is a token
 * delta most of the time, including a leading space. The same field is also, on some steps, a
 * cumulative snapshot of the open segment or a revised snapshot that shares a prefix or a tail
 * with what was already shown. Appending those snapshots splices the rewrite into the old
 * sentence (`whilekept`).
 *
 * Tool calls close the open assistant segment. The next assistant text starts a new paragraph.
 * Thinking is a side channel and does not close the assistant segment.
 * Event ids are remembered so a reconnect that replays the same events does not apply them twice.
 */
internal class RunStreamFold {
    private val seen = HashSet<String>()
    private val assistant = StreamTranscript()
    private val thinking = StreamTranscript()
    private var toolSinceAssistant = false

    fun reset() {
        seen.clear()
        assistant.reset()
        thinking.reset()
        toolSinceAssistant = false
    }

    fun onAssistant(id: String?, text: String) {
        if (!mark(id)) return
        if (toolSinceAssistant) assistant.boundary()
        toolSinceAssistant = false
        assistant.push(text)
    }

    fun onThinking(id: String?, text: String) {
        if (!mark(id)) return
        thinking.push(text)
    }

    fun onTool(id: String?) {
        if (!mark(id)) return
        toolSinceAssistant = true
    }

    fun replaceAssistant(text: String) {
        assistant.replaceAll(text)
        toolSinceAssistant = false
    }

    fun assistantText(): String = assistant.render()

    fun thinkingText(): String = thinking.render()

    private fun mark(id: String?): Boolean {
        if (id.isNullOrBlank()) return true
        return seen.add(id)
    }
}

/** Paragraphs of one channel. A boundary starts a paragraph; it does not join mid-sentence. */
internal class StreamTranscript {
    private val segments = ArrayList<String>()
    private var breakNext = false

    fun reset() {
        segments.clear()
        breakNext = false
    }

    fun boundary() {
        if (segments.isNotEmpty()) breakNext = true
    }

    fun push(text: String) {
        if (text.isEmpty()) return
        val full = render()
        // A snapshot of the whole bubble, including text already closed by a tool call.
        if (full.isNotEmpty() && text.startsWith(full) && (!breakNext || text.startsWith(full + "\n"))) {
            replaceAll(text)
            return
        }
        if (segments.isEmpty() || breakNext) {
            segments.add(text)
            breakNext = false
            return
        }
        val last = segments.lastIndex
        segments[last] = integrateStreamText(segments[last], text)
    }

    fun replaceAll(text: String) {
        segments.clear()
        breakNext = false
        if (text.isNotEmpty()) segments.add(text)
    }

    fun render(): String = segments.joinToString("\n\n")
}

/**
 * Fold one `text` payload into the open segment.
 * Deltas append, including leading whitespace. A snapshot that already contains the segment
 * replaces it. A rewrite that diverges after a shared prefix, or that resends a long tail,
 * replaces the revised span instead of being glued on the end.
 */
internal fun integrateStreamText(current: String, incoming: String): String {
    if (incoming.isEmpty()) return current
    if (current.isEmpty() || incoming == current) return if (current.isEmpty()) incoming else current
    if (incoming.startsWith(current)) return incoming
    if (current.startsWith(incoming) || current.endsWith(incoming)) return current

    val prefix = sharedPrefix(current, incoming)
    if (prefix > 0 && prefix < current.length && prefix < incoming.length) {
        val shorter = minOf(current.length, incoming.length)
        val snapshot = prefix >= SNAPSHOT_PREFIX || (prefix >= 6 && prefix * 2 >= shorter)
        if (snapshot) return incoming
    }

    rewriteOnSharedTail(current, incoming, prefix)?.let { return it }
    return current + incoming
}

internal fun appendAssistantSegment(buf: StringBuilder, text: String, afterOther: Boolean) {
    if (text.isEmpty()) return
    if (buf.isEmpty() || afterOther) {
        if (afterOther && buf.isNotEmpty() && !buf.endsWith("\n")) buf.append("\n\n")
        buf.append(text)
        return
    }
    val rendered = buf.toString()
    val splitAt = rendered.lastIndexOf("\n\n")
    val head = if (splitAt >= 0) rendered.substring(0, splitAt + 2) else ""
    val segment = if (splitAt >= 0) rendered.substring(splitAt + 2) else rendered
    buf.setLength(0)
    buf.append(head).append(integrateStreamText(segment, text))
}

private const val SNAPSHOT_PREFIX = 16
private const val SNAPSHOT_SUFFIX = 24
private const val ANCHOR_MIN = 3
private const val GAP_MAX = 12

private fun sharedPrefix(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

private fun sharedSuffix(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
    return i
}

/**
 * `incoming` repeats a long tail that is already on screen and rewrites the words in front of it.
 * Anchor on the longest suffix of the old head that is a prefix of the new head (`kept`), then
 * drop a short run of replaced words immediately before that anchor (`will be`).
 */
private fun rewriteOnSharedTail(current: String, incoming: String, prefix: Int): String? {
    val suffix = sharedSuffix(current, incoming)
    val room = minOf(current.length, incoming.length) - prefix
    if (suffix < SNAPSHOT_SUFFIX || suffix >= room) return null
    val oldHead = current.substring(0, current.length - suffix)
    val newHead = incoming.substring(0, incoming.length - suffix)
    if (newHead.isEmpty()) return current
    var anchor = 0
    val max = minOf(oldHead.length, newHead.length)
    var len = max
    while (len >= ANCHOR_MIN) {
        if (oldHead.endsWith(newHead.substring(0, len))) {
            anchor = len
            break
        }
        len--
    }
    if (anchor < ANCHOR_MIN) return null
    val before = oldHead.substring(0, oldHead.length - anchor)
    return dropReplacedGap(before, newHead) + incoming
}

private fun dropReplacedGap(before: String, newHead: String): String {
    val words = newHead.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
    var s = before
    var stripped = 0
    var count = 0
    val trailing = Regex("""\s+\S+\s*$""")
    while (count < 3 && stripped < GAP_MAX) {
        val match = trailing.find(s) ?: break
        val token = match.value.trim()
        if (token.length > 12 || token in words) break
        val next = stripped + match.value.length
        if (next > GAP_MAX) break
        val remain = s.substring(0, match.range.first)
        if (remain.isBlank()) break
        stripped = next
        count++
        s = remain
    }
    if (count == 0) return before
    return if (s.lastOrNull() == ' ' || s.lastOrNull() == '\n') s else "$s "
}
