package com.cursorandroid.app.ui.thread

internal const val POLL_FAST_MS = 2_000L
internal const val POLL_FIRST_MS = 1_000L
internal const val POLL_STEADY_MS = 4_000L

/** Fast until the run produces output, then the steady rate. The stream carries output once it flows. */
internal fun runPollDelayMs(receiving: Boolean, tick: Int): Long = when {
    receiving -> POLL_STEADY_MS
    tick == 0 -> POLL_FIRST_MS
    else -> POLL_FAST_MS
}

/** While no stream event has arrived, also read the conversation so server-side output shows up. */
internal fun pullConversationOnTick(receiving: Boolean, tick: Int): Boolean = !receiving && tick % 3 == 2
