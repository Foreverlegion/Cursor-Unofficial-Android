package com.cursorandroid.app.data.repo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GrokChatStore(private val ui: UiPrefsStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): GrokChatSession {
        val raw = ui[UiKeys.grokChatSession] ?: return GrokChatSession()
        return runCatching { json.decodeFromString<GrokChatSession>(raw) }.getOrDefault(GrokChatSession())
    }

    fun save(session: GrokChatSession) {
        ui.put(UiKeys.grokChatSession, json.encodeToString(session.copy(updatedAt = System.currentTimeMillis())))
    }

    fun clear() {
        ui.remove(UiKeys.grokChatSession)
    }
}
