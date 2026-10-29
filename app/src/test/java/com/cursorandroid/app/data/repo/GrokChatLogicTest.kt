package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelParam
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GrokChatLogicTest {
    private val grokFast = ModelItem(id = "grok-4.7-fast", displayName = "Grok 4.7 Fast")
    private val grokHigh = ModelItem(id = "grok-4.7", displayName = "Grok 4.7")
    private val composer = ModelItem(id = "composer-2", displayName = "Composer")
    private lateinit var ui: UiPrefsStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        ui = UiPrefsStore.open(context)
    }

    @After
    fun tearDown() {
        ui.close()
    }

    @Test
    fun filterGrokModelsKeepsOnlyGrokIds() {
        val out = filterGrokModels(listOf(composer, grokFast, grokHigh))
        assertEquals(listOf("grok-4.7", "grok-4.7-fast"), out.map { it.id })
    }

    @Test
    fun pickDefaultPrefersNewerGrok() {
        val picked = pickDefaultGrokModel(listOf(composer, grokFast, grokHigh))
        assertEquals("grok-4.7", picked?.id)
    }

    @Test
    fun createPromptWrapsSystemAndUser() {
        val prompt = grokCreatePrompt("hello")
        assertTrue(prompt.text.contains("casual mobile chat"))
        assertTrue(prompt.text.contains("hello"))
    }

    @Test
    fun createRequestIsNoRepo() {
        val req = grokCreateRequest("hi", grokModelSelection(grokHigh, emptyList()))
        assertNull(req.repos)
        assertNull(req.env)
        assertEquals(GROK_AGENT_NAME, req.name)
    }

    @Test
    fun sessionNeedsNewAgentWhenModelChanges() {
        val session = GrokChatSession(agentId = "bc-1", modelId = "grok-4.6", modelParams = emptyList())
        assertTrue(needsNewGrokAgent(session, "grok-4.7", emptyList()))
        assertFalse(needsNewGrokAgent(session, "grok-4.6", emptyList()))
    }

    @Test
    fun sessionNeedsNewAgentWhenParamsChange() {
        val session = GrokChatSession(agentId = "bc-1", modelId = "grok-4.7", modelParams = listOf(ModelParam("effort", "low")))
        val next = listOf(ModelParam("effort", "high"))
        assertTrue(needsNewGrokAgent(session, "grok-4.7", next))
    }

    @Test
    fun grokStoreRoundTrip() {
        val store = GrokChatStore(ui)
        val session = GrokChatSession(
            agentId = "bc-9",
            modelId = "grok-4.7",
            messages = listOf(GrokMessage("u1", "user", "hi", "run-1")),
        )
        store.save(session)
        val loaded = store.load()
        assertEquals("bc-9", loaded.agentId)
        assertEquals(1, loaded.messages.size)
        store.clear()
        assertNull(store.load().agentId)
    }
}
