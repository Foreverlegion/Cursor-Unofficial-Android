package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.CreateRunRequest
import com.cursorandroid.app.data.api.CreateRunResponse
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.auth.ApiKeyStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FollowUpMcpTest {
    private lateinit var context: Context
    private lateinit var ui: UiPrefsStore
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
    private val sent = mutableListOf<CreateRunRequest>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs", "local_chats")
            .forEach { context.deleteSharedPreferences(it) }
        ui = UiPrefsStore.open(context)
    }

    @After
    fun tearDown() {
        ui.close()
    }

    private fun repo(store: ApiKeyStore): AgentRepository {
        val api = Proxy.newProxyInstance(CursorApi::class.java.classLoader, arrayOf(CursorApi::class.java)) { _, method, args ->
            check(method.name == "createRun") { "unexpected call ${method.name}" }
            sent += args[1] as CreateRunRequest
            check(args.last() is Continuation<*>)
            CreateRunResponse(run = Run(id = "run-2"))
        } as CursorApi
        return AgentRepository(
            api = api,
            sse = SseStreamer(OkHttpClient(), json),
            store = store,
            catalog = CatalogCache(context),
            publicHttp = OkHttpClient(),
            json = json,
        )
    }

    private fun store() = ApiKeyStore(context, ui) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }

    private fun twoServers(store: ApiKeyStore): List<StoredMcpServer> {
        val docs = StoredMcpServer(name = "docs", url = "https://docs.example/mcp")
        val gh = StoredMcpServer(name = "github", url = "https://api.githubcopilot.com/mcp/", headers = mapOf("Authorization" to "Bearer t"))
        store.saveStoredMcps(listOf(docs, gh))
        return listOf(docs, gh)
    }

    @Test
    fun followUpOmitsMcpServersSoTheCreationSetStays() {
        val store = store()
        twoServers(store)
        runBlocking { repo(store).followUp("bc-1", Prompt("next")) }
        assertEquals(1, sent.size)
        assertNull(sent.single().mcpServers)
        val wire = json.encodeToString(sent.single())
        assertFalse(wire, wire.contains("mcpServers"))
    }

    @Test
    fun globalListIsNeverSwappedInOnAnAgentCreatedWithARepoSubset() {
        val store = store()
        val (docs, _) = twoServers(store)
        val atCreate = mcpServersFor(store.storedMcps(), listOf(docs.id))
        assertEquals(listOf("docs"), atCreate?.map { it.name })
        val create = CreateAgentRequest(prompt = Prompt("go"), mcpServers = atCreate)
        assertEquals(listOf("docs"), create.mcpServers?.map { it.name })

        runBlocking { repo(store).followUp("bc-1", Prompt("again")) }
        assertNull(sent.single().mcpServers)
    }

    @Test
    fun anExplicitSelectionForTheChatIsSentAsIs() {
        val store = store()
        val (_, gh) = twoServers(store)
        val chosen = mcpServersFor(store.storedMcps(), listOf(gh.id))
        runBlocking { repo(store).followUp("bc-1", Prompt("again"), mcpServers = chosen) }
        assertEquals(listOf("github"), sent.single().mcpServers?.map { it.name })
        assertNotNull(sent.single().mcpServers?.single()?.headers)
        assertTrue(json.encodeToString(sent.single()).contains("mcpServers"))
    }
}
