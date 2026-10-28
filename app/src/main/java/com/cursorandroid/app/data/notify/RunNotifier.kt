package com.cursorandroid.app.data.notify

import android.annotation.SuppressLint
import android.app.Notification
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
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.repo.InboxRefreshHub
import com.cursorandroid.app.data.repo.LocalChatStore
import com.cursorandroid.app.data.repo.RefreshReason
import com.cursorandroid.app.data.repo.RunSettleHub
import com.cursorandroid.app.data.repo.SafeLinks

class RunNotifier(
    private val context: Context,
    private val store: ApiKeyStore,
    private val notices: NoticeStore,
    private val chats: LocalChatStore,
    private val runSettle: RunSettleHub? = null,
    private val refresh: InboxRefreshHub? = null,
) {
    private val seen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val liveOverrides = mutableMapOf<String, LiveRunOverride>()

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
        manager.createNotificationChannel(
            NotificationChannel(
                LIVE_CHANNEL,
                context.getString(R.string.notify_live_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notify_live_channel_desc)
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
        refresh?.request(RefreshReason.Notification)
        if (skipShade(agentId)) return
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
            .setGroup(AGENT_RUN_NOTIFICATION_GROUP)
            .setContentIntent(openChat(context, agentId, noticeId, notifyId))
            .setDeleteIntent(dismissShade(context, noticeId, notifyId))
            .build()
        NotifyShade.post(context, notifyId, notification)
    }

    fun acknowledgeKnown(agents: List<AgentSummary>) {
        synchronized(seen) {
            seen.edit(commit = true) {
                agents.forEach { agent ->
                    val runId = agent.latestRunId ?: return@forEach
                    if (!isLiveStatus(agent.status)) {
                        putBoolean(runId, true)
                    }
                }
            }
        }
    }

    fun notifyIfNeeded(
        agentId: String,
        agentName: String?,
        runId: String,
        status: String?,
        result: String?,
        prUrl: String? = null,
    ) {
        runSettle?.publish(agentId, runId, status, result)
        refresh?.request(RefreshReason.Notification)
        val title = chatTitle(agentId, agentName)
        val fresh = notices.record(agentId, title, runId, status, result)
        val terminal = status?.uppercase() in TERMINAL
        if (terminal) {
            liveOverrides.remove(agentId)
            clearLiveRun(agentId, runId)
        }
        if (!store.notifyOnComplete) return
        if (!terminal) return
        if (!fresh) return
        if (!claim(runId)) return
        if (skipShade(agentId)) return
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
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(AGENT_RUN_NOTIFICATION_GROUP)
            .setContentIntent(openChat(context, agentId, runId, notifyId))
            .setDeleteIntent(dismissShade(context, runId, notifyId))
        openUrl(context, prUrl, notifyId xor PR_ACTION_SALT)?.let { builder.addAction(0, "Open PR", it) }
        val notification = builder.build()
        NotifyShade.post(context, notifyId, notification)
    }

    fun updateLiveRun(
        agentId: String,
        agentName: String?,
        runId: String,
        agentStatus: String?,
        runStatus: String?,
        activityHint: String? = null,
        approvalPending: Boolean = false,
    ) {
        if (skipShade(agentId)) {
            liveOverrides.remove(agentId)
            clearLiveRun(agentId, runId)
            return
        }
        RunWatchStore.touchActivity(context, agentId, runId)
        liveOverrides[agentId] = LiveRunOverride(
            agentName = agentName,
            runId = runId,
            agentStatus = agentStatus,
            runStatus = runStatus,
            activityHint = activityHint,
            approvalPending = approvalPending,
        )
        refreshLiveRunShade()
    }

    fun syncLiveFromWatchList(agents: List<AgentSummary>) {
        if (!store.liveRunStatus) return
        val byId = agents.associateBy { it.id }
        val watches = RunWatchStore.all(context)
        if (watches.isEmpty()) return
        for (watch in watches) {
            val agent = byId[watch.agentId]
            if (agent != null && watch.runId == agent.latestRunId) {
                liveOverrides[watch.agentId] = LiveRunOverride(
                    agentName = watch.agentName ?: agent.name,
                    runId = watch.runId,
                    agentStatus = agent.status,
                    runStatus = agent.runStatus ?: agent.status,
                )
            }
        }
        refreshLiveRunShade()
    }

    /** Rebuilds promoted, grouped, and summary live notifications from watches and overrides. */
    fun refreshLiveRunShade() {
        if (!store.liveRunStatus) {
            clearAllLiveRuns()
            return
        }
        if (!NotifyPermission.granted(context)) return
        val watches = RunWatchStore.all(context)
        if (watches.isEmpty()) {
            clearAllLiveRunShadeOnly()
            return
        }
        ensureChannel()
        val promotionAllowed = canPromoteLive()
        val rows = watches.mapNotNull { watch -> shadeRow(watch) }
        val plan = planLiveRunShade(
            running = rows,
            maxPromoted = store.maxLivePromoted,
            liveStatusEnabled = store.liveRunStatus,
            promotionAllowed = promotionAllowed,
        )
        val activeIds = rows.map { it.agentId }.toSet()
        watches.filter { it.agentId !in activeIds }.forEach { clearLiveRun(it.agentId, it.runId) }
        val promotedSet = plan.promotedAgentIds.toSet()
        val groupedSet = plan.groupedRows.map { it.agentId }.toSet()
        for (row in rows) {
            cancelLegacyLiveSlots(row.agentId, row.runId)
            val slot = liveNotificationSlot(row.agentId)
            when {
                row.agentId in promotedSet -> {
                    val notification = buildLiveNotification(
                        agentId = row.agentId,
                        runId = row.runId,
                        slot = slot,
                        content = row.toContent(),
                        promoted = true,
                        grouped = false,
                    )
                    NotifyShade.post(context, slot.id, notification, slot.tag)
                }
                row.agentId in groupedSet -> {
                    val notification = buildLiveNotification(
                        agentId = row.agentId,
                        runId = row.runId,
                        slot = slot,
                        content = row.toContent(),
                        promoted = false,
                        grouped = true,
                    )
                    NotifyShade.post(context, slot.id, notification, slot.tag)
                }
                else -> clearLiveRun(row.agentId, row.runId)
            }
        }
        if (plan.showsSummary) {
            val summarySlot = agentRunSummarySlot()
            val summary = buildRunGroupSummary(plan.groupedRows)
            NotifyShade.post(context, summarySlot.id, summary, summarySlot.tag)
        } else {
            cancelRunGroupSummary()
        }
    }

    fun clearLiveRun(agentId: String, runId: String? = null) {
        liveOverrides.remove(agentId)
        val slot = liveNotificationSlot(agentId)
        NotifyShade.cancel(context, slot.id, slot.tag)
        cancelLegacyLiveSlots(agentId, runId)
        maybeClearSummaryAfterAgentRemoved()
    }

    fun clearAllLiveRuns() {
        liveOverrides.clear()
        clearAllLiveRunShadeOnly()
    }

    private fun clearAllLiveRunShadeOnly() {
        RunWatchStore.all(context).forEach { watch ->
            val slot = liveNotificationSlot(watch.agentId)
            NotifyShade.cancel(context, slot.id, slot.tag)
            cancelLegacyLiveSlots(watch.agentId, watch.runId)
        }
        cancelRunGroupSummary()
    }

    private fun maybeClearSummaryAfterAgentRemoved() {
        if (!store.liveRunStatus) {
            cancelRunGroupSummary()
            return
        }
        val watches = RunWatchStore.all(context)
        val rows = watches.mapNotNull { shadeRow(it) }
        val plan = planLiveRunShade(
            running = rows,
            maxPromoted = store.maxLivePromoted,
            liveStatusEnabled = store.liveRunStatus,
            promotionAllowed = canPromoteLive(),
        )
        if (plan.showsSummary) {
            val summarySlot = agentRunSummarySlot()
            NotifyShade.post(context, summarySlot.id, buildRunGroupSummary(plan.groupedRows), summarySlot.tag)
        } else {
            cancelRunGroupSummary()
        }
    }

    private fun cancelRunGroupSummary() {
        val slot = agentRunSummarySlot()
        NotifyShade.cancel(context, slot.id, slot.tag)
    }

    private fun shadeRow(watch: WatchItem): RunningAgentShadeRow? {
        if (skipShade(watch.agentId)) return null
        val override = liveOverrides[watch.agentId]
        val runId = override?.runId?.takeIf { it.isNotBlank() } ?: watch.runId
        val title = chatTitle(watch.agentId, override?.agentName ?: watch.agentName)
        val content = liveRunNotificationContent(
            chatTitle = title,
            agentStatus = override?.agentStatus,
            runStatus = override?.runStatus ?: "RUNNING",
            activityHint = override?.activityHint,
            approvalPending = override?.approvalPending == true,
            startedAtMs = watch.startedAtMs,
        ) ?: return null
        return RunningAgentShadeRow(
            agentId = watch.agentId,
            runId = runId,
            title = content.title,
            body = content.body,
            shortCriticalText = content.shortCriticalText,
            lastActiveMs = watch.lastActiveMs,
        )
    }

    private fun RunningAgentShadeRow.toContent() = LiveRunNotificationContent(
        title = title,
        body = body,
        shortCriticalText = shortCriticalText,
    )

    private fun buildRunGroupSummary(grouped: List<RunningAgentShadeRow>): Notification {
        val count = grouped.size
        val title = context.resources.getQuantityString(
            R.plurals.notify_agent_runs_summary_title,
            count,
            count,
        )
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
        grouped.forEach { row ->
            style.addLine(context.getString(R.string.notify_agent_runs_summary_line, row.title, row.summaryStatus()))
        }
        return NotificationCompat.Builder(context, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(title)
            .setContentText(title)
            .setStyle(style)
            .setGroup(AGENT_RUN_NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun RunningAgentShadeRow.summaryStatus(): String {
        val parts = body.split('·').map { it.trim() }
        return parts.firstOrNull()?.ifBlank { shortCriticalText } ?: shortCriticalText
    }

    private fun canPromoteLive(): Boolean {
        return Build.VERSION.SDK_INT >= PromotedNotificationSettings.MIN_API &&
            store.liveRunStatus &&
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
    }

    private fun cancelLegacyLiveSlots(agentId: String, runId: String?) {
        runId?.let { legacy ->
            val legacySlot = legacyLiveNotificationSlot(legacy)
            NotifyShade.cancel(context, legacySlot.id, legacySlot.tag)
            NotifyShade.cancel(context, legacySlot.id)
        }
        RunWatchStore.all(context)
            .filter { it.agentId == agentId && runId != null && it.runId != runId }
            .forEach { item ->
                val old = legacyLiveNotificationSlot(item.runId)
                NotifyShade.cancel(context, old.id, old.tag)
                NotifyShade.cancel(context, old.id)
            }
    }

    @SuppressLint("NewApi")
    private fun buildLiveNotification(
        agentId: String,
        runId: String,
        slot: LiveNotificationSlot,
        content: LiveRunNotificationContent,
        promoted: Boolean,
        grouped: Boolean,
    ): Notification {
        val open = openChat(context, agentId, runId, slot.id)
        val requestPromote = promoted && canPromoteLive()
        if (Build.VERSION.SDK_INT >= PromotedNotificationSettings.MIN_API) {
            val builder = Notification.Builder(context, LIVE_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_agent)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
            if (requestPromote) {
                builder.setRequestPromotedOngoing(true)
                builder.setShortCriticalText(content.shortCriticalText)
            }
            if (grouped) {
                builder.setGroup(AGENT_RUN_NOTIFICATION_GROUP)
            }
            return builder.build()
        }
        val compat = NotificationCompat.Builder(context, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setProgress(0, 0, true)
        if (grouped) {
            compat.setGroup(AGENT_RUN_NOTIFICATION_GROUP)
        }
        return compat.build()
    }

    private fun skipShade(agentId: String): Boolean {
        return chats.isMuted(agentId) || VisibleAgent.shouldSuppress()
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

    private data class LiveRunOverride(
        val agentName: String? = null,
        val runId: String = "",
        val agentStatus: String? = null,
        val runStatus: String? = null,
        val activityHint: String? = null,
        val approvalPending: Boolean = false,
    )

    companion object {
        const val CHANNEL = "agent_complete"
        const val APPROVAL_CHANNEL = "agent_approval"
        const val LIVE_CHANNEL = "agent_live"
        const val WATCH_CHANNEL = "agent_watch"
        const val EXTRA_AGENT_ID = "agent_id"
        const val EXTRA_NOTICE_ID = "notice_id"
        private const val WATCHING_ID = 41
        private const val PREFS = "notified_runs"
        private val TERMINAL = setOf("FINISHED", "ERROR", "CANCELLED", "EXPIRED")

        private fun seenKey(callId: String) = "approval:$callId"

        fun noticeIdFrom(intent: Intent?): String? {
            if (intent == null) return null
            return noticeIdFrom(intent.getStringExtra(EXTRA_NOTICE_ID), intent.data?.toString())
        }

        fun noticeIdFrom(extra: String?, data: String?): String? {
            extra?.takeIf { it.isNotBlank() }?.let { return it }
            val raw = data?.trim().orEmpty()
            val prefix = "cursor-notice:"
            if (!raw.startsWith(prefix, ignoreCase = true)) return null
            return raw.substring(prefix.length).trim().trimStart('/').takeIf { it.isNotBlank() }
        }

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
            intent.data = Uri.parse("cursor-notice:$noticeId")
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getActivity(context, requestCode, intent, flags)
        }

        private const val PR_ACTION_SALT = 0x5052

        private fun openUrl(context: Context, raw: String?, requestCode: Int): PendingIntent? {
            if (!SafeLinks.isHttps(raw)) return null
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw!!.trim()))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
