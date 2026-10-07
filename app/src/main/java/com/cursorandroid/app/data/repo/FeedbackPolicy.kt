package com.cursorandroid.app.data.repo

/**
 * Anonymous feedback rules. The install correlator stays on the device.
 * GitHub issues carry the report text only, so a reply can be polled by issue number
 * without putting a name, email, key, device, or install id in what Op reads.
 */
object FeedbackPolicy {
    const val OWNER_EMAIL = "foreverlegion@gmail.com"
    const val LABEL_FEEDBACK = "feedback"
    const val LABEL_BANNED = "banned"
    const val BUTTON = "Report bug/request feature"
    const val BLOCKED = "Reports from this install are turned off."
    const val NOTICE =
        "Please use the \"Report bug/request feature\" button in Settings → Account if needed."
    const val ANONYMOUS =
        "Reports are anonymous. Your name, email, API key, and device are not included."

    fun isOperator(email: String?): Boolean {
        return email?.trim()?.equals(OWNER_EMAIL, ignoreCase = true) == true
    }

    fun isBannedLabel(name: String?): Boolean {
        return name?.equals(LABEL_BANNED, ignoreCase = true) == true
    }

    /** True when this install must not create another issue. */
    fun blocksSubmit(localBanned: Boolean, serverBanned: Boolean): Boolean {
        return localBanned || serverBanned
    }
}

/** Local record of filed issue numbers. The mailbox id is never sent. */
interface ReportLedger {
    fun banned(): Boolean
    fun ban()
    fun numbers(): List<Int>
    fun remember(number: Int, title: String)
}
