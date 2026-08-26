package com.cursorandroid.app.data.repo

object FirstChatNotice {
    const val KIND = "notice"
    const val ID = "notice-first"

    const val CLOUD =
        "The first reply can take a little longer. A new cloud environment is starting."
    const val MACHINE =
        "The first reply can take a little longer. Connecting to the machine."
    const val POOL =
        "The first reply can take a little longer. Starting the environment."

    fun text(envType: String?): String {
        return when (envType?.trim()?.lowercase()) {
            "machine" -> MACHINE
            "pool" -> POOL
            else -> CLOUD
        }
    }

    fun line(envType: String? = null): TranscriptLine {
        return TranscriptLine(id = ID, kind = KIND, text = text(envType))
    }
}

fun List<TranscriptLine>.withStartupNotice(envType: String?): List<TranscriptLine> {
    if (any { it.kind == FirstChatNotice.KIND || it.id == FirstChatNotice.ID }) return this
    return listOf(FirstChatNotice.line(envType)) + this
}
