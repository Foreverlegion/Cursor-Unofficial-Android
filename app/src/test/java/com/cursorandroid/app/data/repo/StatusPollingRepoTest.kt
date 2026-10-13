package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.AgentDetail
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.auth.ApiKeyStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StatusPollingRepoTest {
    private lateinit var context: Context
    private lateinit var ui: UiPrefsStore
    private lateinit var catalog: CatalogCache
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
    private var clock = 1_800_000_000_000L
    private val agentCalls = ArrayList<String>()
    private val runCalls = ArrayList<String>()
    private var limitWith: String? = null
    private var limited = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs", "local_chats", "catalog")
            .forEach { context.deleteSharedPreferences(it) }
        ui = UiPrefsStore.open(context)
        catalog = CatalogCache(context)
    }

    @After
    fun tearDown() {
        ui.close()
    }

    private fun tooMany(): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://api.cursor.com/v1/agents/x").build())
            .apply { limitWith?.let { header("Retry-After", it) } }
            .build()
        val body = """{"error":{"code":"rate_limit_exceeded","message":"You have exceeded the rate limit of 6000 requests per hour for the get_agent_status endpoint"}}"""
        return HttpException(Response.error<Any>(body.toResponseBody("application/json".toMediaType()), raw))
    }

    private fun repo(): AgentRepository {
        val api = Proxy.newProxyInstance(CursorApi::class.java.classLoader, arrayOf(CursorApi::class.java)) { _, method, args ->
            when (method.name) {
                "getAgent" -> {
                    agentCalls += args[0] as String
                    if (limited) throw tooMany()
                    AgentDetail(id = args[0] as String, status = "RUNNING", latestRunId = "r1")
                }
                "getRun" -> {
                    runCalls += args[1] as String
                    if (limited) throw tooMany()
                    Run(id = args[1] as String, status = "FINISHED")
                }
                else -> error("unexpected call ${method.name}")
            }
        } as CursorApi
        val store = ApiKeyStore(context, ui) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
        return AgentRepository(
            api = api,
            sse = SseStreamer(OkHttpClient(), json),
            store = store,
            catalog = catalog,
            publicHttp = OkHttpClient(),
            json = json,
            statusGate = StatusGate(now = { clock }),
        )
    }

    private fun summary(id: String, status: String?) = AgentSummary(id = id, status = status, latestRunId = "r-$id")

    @Test
    fun hydratingTheInboxNeverAsksAboutFinishedAgents() = runBlocking {
        val repo = repo()
        val list = listOf(summary("done", "FINISHED"), summary("err", "ERROR"), summary("expired", "EXPIRED"))
        repo.hydrateStatuses(list)
        assertTrue(agentCalls.toString(), agentCalls.isEmpty())
    }

    @Test
    fun hydratingAsksOnlyAboutLiveAgentsAndMissingStatuses() = runBlocking {
        val repo = repo()
        repo.hydrateStatuses(listOf(summary("done", "FINISHED"), summary("run", "RUNNING"), summary("blank", null)))
        assertEquals(setOf("run", "blank"), agentCalls.toSet())
        assertEquals(2, agentCalls.size)
    }

    @Test
    fun resolvingRepoUrlsForFinishedAgentsStillWorksButIsBoundedAndOneAtATimePerAgent() = runBlocking {
        val repo = repo()
        val many = (1..40).map { summary("a$it", "FINISHED") }
        repo.resolveAgentRepos(many)
        assertEquals(15, agentCalls.size)
        agentCalls.clear()
        repo.resolveAgentRepos(many)
        assertEquals(15, agentCalls.size)
        assertEquals(15, agentCalls.toSet().size)
    }

    @Test
    fun concurrentCallersForOneAgentShareASingleRequest() = runBlocking {
        val repo = repo()
        repeat(5) { repo.getAgent("same") }
        assertEquals(1, agentCalls.size)
        repeat(5) { repo.getRun("same", "r1") }
        assertEquals(1, runCalls.size)
    }

    @Test
    fun a429TurnsIntoAnApiExceptionWithRetryAfterAndPausesEveryStatusCall() = runBlocking {
        val repo = repo()
        limited = true
        limitWith = "30"
        try {
            repo.getAgent("a")
            fail("expected a 429")
        } catch (e: ApiException) {
            assertTrue(e.isRateLimited)
            assertEquals(30_000L, e.retryAfterMs)
        }
        assertEquals(1, agentCalls.size)
        assertEquals(30_000L, repo.statusGate.remainingMs())

        limited = false
        repeat(10) {
            try {
                repo.getAgent("b")
                fail("the gate should hold the call back")
            } catch (e: ApiException) {
                assertTrue(e.isRateLimited)
            }
            try {
                repo.getRun("b", "r")
                fail("the gate should hold the call back")
            } catch (_: ApiException) {
            }
        }
        assertEquals(1, agentCalls.size)
        assertTrue(runCalls.isEmpty())

        val inboxList = listOf(summary("run", "RUNNING"))
        repo.hydrateStatuses(inboxList)
        repo.resolveAgentRepos(inboxList)
        assertEquals(1, agentCalls.size)
    }

    @Test
    fun theGateReopensAfterTheRetryAfterAndRecoversOnSuccess() = runBlocking {
        val repo = repo()
        limited = true
        limitWith = "30"
        try {
            repo.getAgent("a")
        } catch (_: ApiException) {
        }
        clock += 31_000
        limited = false
        assertEquals("a", repo.getAgent("a").id)
        assertFalse(repo.statusGate.isPaused)
        assertEquals(2, agentCalls.size)
    }

    @Test
    fun withoutARetryAfterTheBackoffStartsAtFiveSecondsAndDoubles() = runBlocking {
        val repo = repo()
        limited = true
        limitWith = null
        try {
            repo.getAgent("a")
        } catch (_: ApiException) {
        }
        assertEquals(5_000L, repo.statusGate.remainingMs())
        clock += 5_001
        try {
            repo.getAgent("a2")
        } catch (_: ApiException) {
        }
        assertEquals(10_000L, repo.statusGate.remainingMs())
    }
}
