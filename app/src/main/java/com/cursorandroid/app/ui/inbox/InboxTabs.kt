package com.cursorandroid.app.ui.inbox

enum class InboxTab {
    Agents,
    Envs,
    Remote,
    ;

    val title: String
        get() = when (this) {
            Agents -> "Agents"
            Envs -> "ENVs"
            Remote -> "Remote"
        }
}

object InboxTabs {
    fun visible(showEnvs: Boolean, showRemote: Boolean): List<InboxTab> = buildList {
        add(InboxTab.Agents)
        if (showEnvs) add(InboxTab.Envs)
        if (showRemote) add(InboxTab.Remote)
    }
}

internal fun inboxEmptyCopy(
    showHidden: Boolean,
    showArchived: Boolean,
    workingOnly: Boolean,
    query: String = "",
): String = when {
    showHidden && showArchived ->
        "No hidden or archived chats. Turn off Hidden or Archived to see the rest."
    showHidden -> "No hidden chats. Turn off Hidden to see the rest."
    showArchived -> "No archived chats. Turn off Archived to see the rest."
    workingOnly -> "Nothing working. Turn off Working to see the rest."
    query.isNotBlank() -> "No chats match this search."
    else -> "No agents yet. Start one on a cloud VM or a named machine."
}
