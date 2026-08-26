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
