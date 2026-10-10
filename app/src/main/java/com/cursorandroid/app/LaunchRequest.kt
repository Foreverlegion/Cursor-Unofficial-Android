package com.cursorandroid.app

import android.content.Intent
import android.net.Uri
import com.cursorandroid.app.data.repo.SafeLinks

data class LaunchRequest(
    val nonce: Long = 0L,
    val agentId: String? = null,
    val invalidAgentLink: Boolean = false,
    val compose: Boolean = false,
    val shareText: String? = null,
    val shareUris: List<Uri> = emptyList(),
    val openSettings: Boolean = false,
) {
    companion object {
        const val INVALID_AGENT_LINK = "That agent link is not valid."

        fun from(intent: Intent?, nonce: Long): LaunchRequest {
            if (intent == null) return LaunchRequest(nonce)
            val notifyId = SafeLinks.agentId(
                intent.getStringExtra(com.cursorandroid.app.data.notify.RunNotifier.EXTRA_AGENT_ID),
            )
            val web = if (intent.action == Intent.ACTION_VIEW) {
                SafeLinks.agentLink(intent.dataString)
            } else {
                null
            }
            val shared = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
            val text = if (shared) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.ifBlank { null }
            } else {
                null
            }
            val uris = shareUris(intent)
            return LaunchRequest(
                nonce = nonce,
                agentId = notifyId ?: web?.agentId,
                invalidAgentLink = notifyId == null && web?.invalid == true,
                compose = shared && notifyId == null && web?.agentId == null,
                shareText = text,
                shareUris = uris,
                openSettings = intent.getBooleanExtra(
                    com.cursorandroid.app.data.notify.FeedbackNotifier.EXTRA_OPEN_SETTINGS,
                    false,
                ),
            )
        }

        @Suppress("DEPRECATION")
        private fun shareUris(intent: Intent): List<Uri> {
            val one = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val many = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            return (listOfNotNull(one) + many).distinct()
        }
    }
}
