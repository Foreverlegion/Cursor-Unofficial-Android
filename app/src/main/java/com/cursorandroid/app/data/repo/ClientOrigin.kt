package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.Prompt

object ClientOrigin {
    const val ID = "cursor-android" // pragma: allowlist secret
    const val PREFIX = "[client=$ID]"

    fun stamp(prompt: Prompt): Prompt {
        val text = prompt.text
        if (text.startsWith(PREFIX)) return prompt
        return prompt.copy(text = "$PREFIX\n\n$text")
    }
}
