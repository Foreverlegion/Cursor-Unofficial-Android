package com.cursorandroid.app

import android.content.Intent
import android.net.Uri
import com.cursorandroid.app.data.repo.SafeLinks

data class LaunchRequest(
    val nonce: Long = 0L,
    val agentId: String? = null,
    val compose: Boolean = false,
    val shareText: String? = null,
    val shareUris: List<Uri> = emptyList(),
) {
    companion object {
        fun from(intent: Intent?, nonce: Long): LaunchRequest {
            if (intent == null) return LaunchRequest(nonce)
            val notifyId = intent.getStringExtra(com.cursorandroid.app.data.notify.RunNotifier.EXTRA_AGENT_ID)
            val viewId = viewAgentId(intent)
            val shared = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
            val text = if (shared) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.ifBlank { null }
            } else {
                null
            }
            val uris = shareUris(intent)
            return LaunchRequest(
                nonce = nonce,
                agentId = SafeLinks.agentId(notifyId ?: viewId),
                compose = shared && notifyId == null && viewId == null,
                shareText = text,
                shareUris = uris,
            )
        }

        private fun viewAgentId(intent: Intent): String? {
            if (intent.action != Intent.ACTION_VIEW) return null
            val path = intent.data?.path.orEmpty()
            val last = path.trimEnd('/').substringAfterLast('/')
            return SafeLinks.agentId(last)
        }

        @Suppress("DEPRECATION")
        private fun shareUris(intent: Intent): List<Uri> {
            val one = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val many = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            return (listOfNotNull(one) + many).distinct()
        }
    }
}
