package com.cursorandroid.app.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cursorandroid.app.CursorAndroidApp
import com.cursorandroid.app.MainActivity
import com.cursorandroid.app.R
import com.cursorandroid.app.data.repo.FeedbackStore
import com.cursorandroid.app.data.repo.FeedbackSync
import java.util.concurrent.TimeUnit

object FeedbackReplyScheduler {
    private const val PERIODIC = "feedback-replies"
    private const val ONCE = "feedback-replies-now"

    fun sync(context: Context) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        if (FeedbackStore(app).numbers().isEmpty()) {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(ONCE)
            return
        }
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FeedbackReplyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(net)
                .build(),
        )
        wm.enqueueUniqueWork(
            ONCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FeedbackReplyWorker>().setConstraints(net).build(),
        )
    }
}

class FeedbackReplyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CursorAndroidApp ?: return Result.success()
        val replies = runCatching { FeedbackSync.unseen(app.container.feedback) }.getOrDefault(emptyList())
        if (!VisibleAgent.shouldSuppress() && NotifyPermission.granted(applicationContext)) {
            replies.take(5).forEach { reply ->
                FeedbackNotifier.post(applicationContext, reply.commentId, reply.body)
            }
        }
        return Result.success()
    }
}

object FeedbackNotifier {
    const val EXTRA_OPEN_SETTINGS = "open_feedback_settings"
    private const val CHANNEL = "feedback_reply"

    fun post(context: Context, commentId: Long, body: String) {
        ensureChannel(context)
        val text = body.replace('\n', ' ').take(140)
        val notifyId = shadeId("feedback-$commentId")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle("Feedback reply")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(400)))
            .setAutoCancel(true)
            .setContentIntent(openSettings(context, notifyId))
            .build()
        NotifyShade.post(context, notifyId, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notify_feedback_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notify_feedback_channel_desc)
            },
        )
    }

    private fun openSettings(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        intent.setPackage(context.packageName)
        intent.putExtra(EXTRA_OPEN_SETTINGS, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }
}
