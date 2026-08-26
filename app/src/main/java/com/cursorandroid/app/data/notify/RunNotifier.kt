package com.cursorandroid.app.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.cursorandroid.app.MainActivity
import com.cursorandroid.app.R
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.repo.LocalChatStore

class RunNotifier(
    private val context: Context,
    private val store: ApiKeyStore,
    private val notices: NoticeStore,
    private val chats: LocalChatStore,
) {
    private val seen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notify_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notify_channel_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                APPROVAL_CHANNEL,
                context.getString(R.string.notify_approval_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notify_approval_channel_desc)
            },
        )
        manager.deleteNotificationChannel(WATCH_CHANNEL)
        NotifyShade.cancel(context, WATCHING_ID)
    }

    fun notifyApproval(
        agentId: String,
        agentName: String?,
        runId: String,
        callId: String?,
        name: String?,
        status: String?,
        args: String?,
    ) {
        if (!store.notifyOnApproval) return
        if (!ApprovalCopy.isApproval(name, status)) return
        val ask = runCatching { ApprovalCopy.ask(name, args, status) }.getOrNull() ?: return
        val id = callId?.takeIf { it.isNotBlank() } ?: "$runId:${name.orEmpty()}"
        if (!claim(seenKey(id))) return
        val noticeId = "approval-$id"
        val title = chatTitle(agentId, agentName)
        if (!notices.recordApproval(agentId, title, id, ask.body)) return
        if (VisibleAgent.shouldSuppress()) return
        if (!NotifyPermission.granted(context)) return
        ensureChannel()
        val notifyId = shadeId(noticeId)
        val notification = NotificationCompat.Builder(context, APPROVAL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(title)
            .setContentText(ask.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(ask.body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openChat(context, agentId, noticeId, notifyId))
            .setDeleteIntent(dismissShade(context, noticeId, notifyId))
            .build()
        NotifyShade.post(context, notifyId, notification)
    }

    fun notifyIfNeeded(agentId: String, agentName: String?, runId: String, status: String?, result: String?) {
        val title = chatTitle(agentId, agentName)
        val fresh = notices.record(agentId, title, runId, status, result)
        if (!store.notifyOnComplete) return
        val terminal = status?.uppercase() in TERMINAL
        if (!terminal) return
        if (!fresh) return
        if (!claim(runId)) return
        if (VisibleAgent.shouldSuppress()) return
        if (!NotifyPermission.granted(context)) return

        ensureChannel()
        val body = buildString {
            append(statusLabel(status))
            val snippet = result?.trim()?.replace('\n', ' ')
            if (!snippet.isNullOrBlank()) {
                append(" — ")
                append(snippet.take(140))
            }
        }
        val notifyId = shadeId(runId)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openChat(context, agentId, runId, notifyId))
            .setDeleteIntent(dismissShade(context, runId, notifyId))
            .build()
        NotifyShade.post(context, notifyId, notification)
    }

    fun rememberDismissed(noticeId: String) {
        val keys = linkedSetOf(noticeId)
        if (noticeId.startsWith("approval-")) {
            keys.add(seenKey(noticeId.removePrefix("approval-")))
        }
        synchronized(seen) {
            seen.edit(commit = true) {
                keys.forEach { putBoolean(it, true) }
            }
        }
    }

    private fun claim(key: String): Boolean {
        synchronized(seen) {
            if (seen.getBoolean(key, false)) return false
            seen.edit(commit = true) { putBoolean(key, true) }
            return true
        }
    }

    private fun chatTitle(agentId: String, fallback: String?): String {
        return chats.displayName(agentId, fallback)
    }

    private fun statusLabel(status: String?): String {
        return when (status?.uppercase()) {
            "FINISHED" -> "Finished"
            "ERROR" -> "Failed"
            "CANCELLED" -> "Cancelled"
            "EXPIRED" -> "Expired"
            else -> status ?: "Updated"
        }
    }

    companion object {
        const val CHANNEL = "agent_complete"
        const val APPROVAL_CHANNEL = "agent_approval"
        const val WATCH_CHANNEL = "agent_watch"
        const val EXTRA_AGENT_ID = "agent_id"
        const val EXTRA_NOTICE_ID = "notice_id"
        private const val WATCHING_ID = 41
        private const val PREFS = "notified_runs"
        private val TERMINAL = setOf("FINISHED", "ERROR", "CANCELLED", "EXPIRED")

        private fun seenKey(callId: String) = "approval:$callId"

        private fun openChat(
            context: Context,
            agentId: String?,
            noticeId: String,
            requestCode: Int,
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
            intent.setClass(context, MainActivity::class.java)
            intent.setPackage(context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (!agentId.isNullOrBlank()) {
                intent.putExtra(EXTRA_AGENT_ID, agentId)
            }
            intent.putExtra(EXTRA_NOTICE_ID, noticeId)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getActivity(context, requestCode, intent, flags)
        }

        private fun dismissShade(context: Context, noticeId: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, NoticeDismissReceiver::class.java)
            intent.setPackage(context.packageName)
            intent.action = NoticeDismissReceiver.ACTION
            intent.putExtra(NoticeDismissReceiver.EXTRA_ID, noticeId)
            intent.data = Uri.parse("cursor-notice://$noticeId")
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }
    }
}
