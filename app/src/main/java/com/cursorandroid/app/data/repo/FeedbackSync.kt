package com.cursorandroid.app.data.repo

object FeedbackSync {
    data class Reply(val issue: Int, val commentId: Long, val body: String)

    data class Thread(val number: Int, val title: String, val replies: List<String>, val reachable: Boolean)

    data class Inbox(val banned: Boolean, val threads: List<Thread>)

    data class Incoming(val number: Int, val title: String, val body: String, val banned: Boolean)

    fun load(ledger: FeedbackStore): Inbox {
        val token = GithubAppIssues.bakedToken()
        val reports = ledger.reports()
        if (token == null) {
            return Inbox(
                banned = ledger.banned(),
                threads = reports.map { Thread(it.number, it.title, emptyList(), reachable = false) },
            )
        }
        var serverBanned = false
        val threads = reports.map { report ->
            val issue = GithubAppIssues.getIssue(token, report.number)
            if (issue != null && issue.labels.any { FeedbackPolicy.isBannedLabel(it.name) }) {
                serverBanned = true
            }
            val comments = GithubAppIssues.listComments(token, report.number)
            if (comments != null) {
                ledger.markSeen(report.number, comments.mapNotNull { it.id.takeIf { id -> id > 0 } })
            }
            Thread(
                number = report.number,
                title = report.title,
                replies = comments.orEmpty().mapNotNull { it.body?.trim()?.takeIf { text -> text.isNotEmpty() } },
                reachable = comments != null,
            )
        }
        if (serverBanned) ledger.ban()
        return Inbox(banned = ledger.banned(), threads = threads)
    }

    /** Comments not yet marked seen. Call after [load] only for the background poll, which marks seen itself. */
    fun unseen(ledger: FeedbackStore): List<Reply> {
        val token = GithubAppIssues.bakedToken() ?: return emptyList()
        val found = mutableListOf<Reply>()
        var serverBanned = false
        for (report in ledger.reports()) {
            val issue = GithubAppIssues.getIssue(token, report.number)
            if (issue != null && issue.labels.any { FeedbackPolicy.isBannedLabel(it.name) }) {
                serverBanned = true
            }
            val comments = GithubAppIssues.listComments(token, report.number) ?: continue
            val seen = report.seen.toSet()
            comments.forEach { comment ->
                val body = comment.body?.trim().orEmpty()
                if (comment.id > 0 && comment.id !in seen && body.isNotEmpty()) {
                    found.add(Reply(report.number, comment.id, body))
                }
            }
            ledger.markSeen(report.number, comments.map { it.id }.filter { it > 0 })
        }
        if (serverBanned) ledger.ban()
        return found
    }

    fun incoming(): List<Incoming> {
        val token = GithubAppIssues.bakedToken() ?: return emptyList()
        return GithubAppIssues.listLabeled(token, FeedbackPolicy.LABEL_FEEDBACK).map { issue ->
            Incoming(
                number = issue.number ?: return@map null,
                title = issue.title?.trim().orEmpty().ifBlank { "Feedback" },
                body = issue.body?.trim().orEmpty(),
                banned = issue.labels.any { FeedbackPolicy.isBannedLabel(it.name) },
            )
        }.filterNotNull()
    }

    fun ban(number: Int): Boolean {
        val token = GithubAppIssues.bakedToken() ?: return false
        return GithubAppIssues.addLabel(token, number, FeedbackPolicy.LABEL_BANNED, "b60205")
    }

    fun reply(number: Int, text: String): Boolean {
        val token = GithubAppIssues.bakedToken() ?: return false
        val body = BugReport.sanitizeBody(text)
        if (body.isEmpty()) return false
        return GithubAppIssues.createComment(token, number, body)
    }
}
