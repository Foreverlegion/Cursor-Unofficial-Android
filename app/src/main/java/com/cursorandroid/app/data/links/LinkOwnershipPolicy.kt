package com.cursorandroid.app.data.links

enum class LinkOwnershipDialogKind {
    None,
    GrokConflict,
    GrokStillConflict,
    EnableOwnLinks,
}

data class LinkOwnershipDecision(
    val dialog: LinkOwnershipDialogKind,
    val settingsSummary: String,
    val showSuccessSnackbar: Boolean,
    val clearPendingGrok: Boolean,
    val clearPendingOwn: Boolean,
)

object LinkOwnershipPolicy {
    const val GROK_TITLE = "Grok Bot opens Cursor links"
    const val GROK_BODY =
        "The Grok Bot app has verified ownership of cursor.com links on this phone. Agent links open there instead of in Cursor, and Android will not let you turn on just cursor.com for this app while Grok Bot keeps those links. Turn off Open supported links for Grok Bot, then enable cursor.com here."
    const val GROK_STILL_BODY =
        "Grok Bot still handles cursor.com links on this phone. Turn off Open supported links for Grok Bot if you want Cursor agent links to open here."
    const val OWN_TITLE = "Turn on Cursor agent links"
    const val OWN_BODY =
        "Enable Supported web addresses for cursor.com and www.cursor.com under this app's Open by default settings so agent links open here."
    const val SUCCESS = "All set, Cursor links open here now."
    const val TAKE_ME_THERE = "Take me there"
    const val REMIND_ME = "Remind me"
    const val DONT_SHOW_AGAIN = "Don't show again"

    fun decide(
        snap: LinkOwnershipSnapshot,
        dismissed: Boolean,
        pendingGrok: Boolean,
        pendingOwn: Boolean,
        manual: Boolean,
    ): LinkOwnershipDecision {
        val summary = settingsSummary(snap)
        if (!snap.relevant) {
            return LinkOwnershipDecision(
                dialog = LinkOwnershipDialogKind.None,
                settingsSummary = summary,
                showSuccessSnackbar = false,
                clearPendingGrok = pendingGrok,
                clearPendingOwn = pendingOwn,
            )
        }
        val grokOwns = snap.linkHandler == AgentLinkHandler.GrokBot
        val ready = !grokOwns && snap.ownHostsEnabled
        if (ready) {
            return LinkOwnershipDecision(
                dialog = LinkOwnershipDialogKind.None,
                settingsSummary = summary,
                showSuccessSnackbar = pendingGrok || pendingOwn,
                clearPendingGrok = true,
                clearPendingOwn = true,
            )
        }
        if (dismissed && !manual && !pendingGrok && !pendingOwn) {
            return LinkOwnershipDecision(
                dialog = LinkOwnershipDialogKind.None,
                settingsSummary = summary,
                showSuccessSnackbar = false,
                clearPendingGrok = false,
                clearPendingOwn = false,
            )
        }
        if (pendingGrok && grokOwns) {
            return LinkOwnershipDecision(
                dialog = LinkOwnershipDialogKind.GrokStillConflict,
                settingsSummary = summary,
                showSuccessSnackbar = false,
                clearPendingGrok = false,
                clearPendingOwn = false,
            )
        }
        if (pendingGrok && !grokOwns && !snap.ownHostsEnabled) {
            return LinkOwnershipDecision(
                dialog = LinkOwnershipDialogKind.EnableOwnLinks,
                settingsSummary = summary,
                showSuccessSnackbar = false,
                clearPendingGrok = true,
                clearPendingOwn = pendingOwn,
            )
        }
        if (pendingOwn && !snap.ownHostsEnabled) {
            return LinkOwnershipDecision(
                dialog = LinkOwnershipDialogKind.EnableOwnLinks,
                settingsSummary = summary,
                showSuccessSnackbar = false,
                clearPendingGrok = false,
                clearPendingOwn = false,
            )
        }
        if (grokOwns && (!dismissed || manual || pendingGrok)) {
            val kind = if (pendingGrok) {
                LinkOwnershipDialogKind.GrokStillConflict
            } else {
                LinkOwnershipDialogKind.GrokConflict
            }
            return LinkOwnershipDecision(
                dialog = kind,
                settingsSummary = summary,
                showSuccessSnackbar = false,
                clearPendingGrok = false,
                clearPendingOwn = false,
            )
        }
        if (!grokOwns && !snap.ownHostsEnabled && (!dismissed || manual || pendingOwn)) {
            return LinkOwnershipDecision(
                dialog = LinkOwnershipDialogKind.EnableOwnLinks,
                settingsSummary = summary,
                showSuccessSnackbar = false,
                clearPendingGrok = pendingGrok,
                clearPendingOwn = false,
            )
        }
        return LinkOwnershipDecision(
            dialog = LinkOwnershipDialogKind.None,
            settingsSummary = summary,
            showSuccessSnackbar = false,
            clearPendingGrok = false,
            clearPendingOwn = false,
        )
    }

    fun settingsSummary(snap: LinkOwnershipSnapshot): String {
        if (!snap.relevant) return "Cursor agent links open in this app"
        if (snap.linkHandler == AgentLinkHandler.GrokBot) {
            return "Grok Bot opens cursor.com links"
        }
        if (!snap.ownHostsEnabled) {
            return "Turn on Supported web addresses"
        }
        return "Cursor agent links open in this app"
    }
}
