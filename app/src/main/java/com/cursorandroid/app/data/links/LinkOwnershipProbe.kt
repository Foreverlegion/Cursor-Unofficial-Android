package com.cursorandroid.app.data.links

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager.ResolveInfoFlags
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import androidx.core.content.getSystemService
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState

enum class AgentLinkHandler {
    GrokBot,
    ThisApp,
    Other,
}

data class LinkOwnershipSnapshot(
    val relevant: Boolean,
    val grokInstalled: Boolean,
    val linkHandler: AgentLinkHandler,
    val ownHostsEnabled: Boolean,
)

class LinkOwnershipProbe(
    private val ownPackage: String,
    private val grokPackage: String,
    private val sdkInt: Int,
    private val grokInstalled: () -> Boolean,
    private val resolveHandler: () -> AgentLinkHandler,
    private val ownHostsEnabled: () -> Boolean,
) {
    fun snapshot(): LinkOwnershipSnapshot {
        if (sdkInt < Build.VERSION_CODES.S) {
            return LinkOwnershipSnapshot(
                relevant = false,
                grokInstalled = false,
                linkHandler = AgentLinkHandler.Other,
                ownHostsEnabled = true,
            )
        }
        val installed = grokInstalled()
        return LinkOwnershipSnapshot(
            relevant = installed,
            grokInstalled = installed,
            linkHandler = resolveHandler(),
            ownHostsEnabled = ownHostsEnabled(),
        )
    }

    companion object {
        const val GROK_PACKAGE = "ai.x.grok.bot"
        private val PROBE_URI = Uri.parse("https://cursor.com/agents/test")
        private val OWN_HOSTS = listOf("cursor.com", "www.cursor.com")

        fun from(context: Context): LinkOwnershipProbe {
            val app = context.applicationContext
            val pm = app.packageManager
            val own = app.packageName
            return LinkOwnershipProbe(
                ownPackage = own,
                grokPackage = GROK_PACKAGE,
                sdkInt = Build.VERSION.SDK_INT,
                grokInstalled = { isPackageInstalled(pm, GROK_PACKAGE) },
                resolveHandler = { resolveAgentLinkHandler(pm, own, GROK_PACKAGE) },
                ownHostsEnabled = { readOwnHostsEnabled(app, own) },
            )
        }

        internal fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
            return runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                }
                true
            }.getOrDefault(false)
        }

        internal fun resolveAgentLinkHandler(
            pm: PackageManager,
            ownPackage: String,
            grokPackage: String,
        ): AgentLinkHandler {
            val intent = Intent(Intent.ACTION_VIEW, PROBE_URI).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val resolved = if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveActivity(intent, ResolveInfoFlags.of(MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, MATCH_DEFAULT_ONLY)
            }
            val pkg = resolved?.activityInfo?.packageName ?: return AgentLinkHandler.Other
            return when (pkg) {
                grokPackage -> AgentLinkHandler.GrokBot
                ownPackage -> AgentLinkHandler.ThisApp
                else -> AgentLinkHandler.Other
            }
        }

        internal fun readOwnHostsEnabled(context: Context, ownPackage: String): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val manager = context.getSystemService<DomainVerificationManager>() ?: return false
            val state = runCatching {
                manager.getDomainVerificationUserState(ownPackage)
            }.getOrNull() ?: return false
            val map = state.hostToStateMap
            return OWN_HOSTS.all { host -> hostEnabled(map[host]) }
        }

        private fun hostEnabled(state: Int?): Boolean {
            if (state == null) return false
            return state == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
        }
    }
}
