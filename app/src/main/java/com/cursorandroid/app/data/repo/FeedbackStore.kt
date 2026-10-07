package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class FeedbackStore(context: Context) : ReportLedger {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun banned(): Boolean = read().banned

    override fun ban() {
        synchronized(this) {
            val current = read()
            if (current.banned) return
            write(current.copy(banned = true))
        }
    }

    override fun numbers(): List<Int> = read().reports.map { it.number }

    override fun remember(number: Int, title: String) {
        synchronized(this) {
            val current = read()
            if (current.reports.any { it.number == number }) return
            val next = (current.reports + StoredReport(number = number, title = title.take(80))).takeLast(20)
            write(current.copy(reports = next))
        }
    }

    fun reports(): List<StoredReport> = read().reports

    fun markSeen(number: Int, ids: List<Long>) {
        synchronized(this) {
            val current = read()
            val next = current.reports.map { report ->
                if (report.number != number) {
                    report
                } else {
                    report.copy(seen = (report.seen + ids).distinct().takeLast(200))
                }
            }
            write(current.copy(reports = next))
        }
    }

    private fun read(): FeedbackFile {
        val raw = prefs.getString(KEY, null)
        val parsed = raw?.let { runCatching { json.decodeFromString<FeedbackFile>(it) }.getOrNull() }
        if (parsed != null && parsed.mailbox.isNotBlank()) return parsed
        val created = FeedbackFile(mailbox = UUID.randomUUID().toString())
        write(created)
        return created
    }

    private fun write(file: FeedbackFile) {
        prefs.edit { putString(KEY, json.encodeToString(file)) }
    }

    @Serializable
    data class FeedbackFile(
        val mailbox: String,
        val banned: Boolean = false,
        val reports: List<StoredReport> = emptyList(),
    )

    @Serializable
    data class StoredReport(
        val number: Int,
        val title: String,
        val seen: List<Long> = emptyList(),
    )

    private companion object {
        const val PREFS = "cursor_feedback"
        const val KEY = "mailbox"
    }
}
