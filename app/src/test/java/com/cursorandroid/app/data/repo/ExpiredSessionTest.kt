package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.AgentDetail
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.MeResponse
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.auth.SessionGuard
import com.cursorandroid.app.data.notify.RunWatchBackoff
import com.cursorandroid.app.data.notify.sweepTargets
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
class ExpiredSessionTest {
    private lateinit var context: Context
    private lateinit var ui: UiPrefsStore
    private lateinit var catalog: CatalogCache
    private lateinit var store: ApiKeyStore
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
    private val calls = ArrayList<String>()
    private var rejectKey = true

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs", "local_chats", "catalog")
            .forEach { context.deleteSharedPreferences(it) }
        ui = UiPrefsStore.open(context)
        catalog = CatalogCache(context)
        store = ApiKeyStore(context, ui) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
        store.apiKey = "key_REVOKED"
    }

    @After
    fun tearDown() {
        ui.close()
    }

    private fun unauthorized(): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(401)
            .message("Unauthorized")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://api.cursor.com/v1/me").build())
            .build()
        val body = """{"error":{"code":"unauthorized","message":"Invalid User API Key"}}"""
        return HttpException(Response.error<Any>(body.toResponseBody("application/json".toMediaType()), raw))
    }

    private fun repo(): AgentRepository {
        val api = Proxy.newProxyInstance(CursorApi::class.java.classLoader, arrayOf(CursorApi::class.java)) { _, method, args ->
            calls += method.name
            if (rejectKey && store.apiKey == "key_REVOKED") throw unauthorized()
            when (method.name) {
                "me" -> MeResponse(userEmail = "a@b.c")
                "getAgent" -> AgentDetail(id = args[0] as String, status = "RUNNING")
                "getRun" -> Run(id = args[1] as String, status = "RUNNING")
                else -> error("unexpected call ${method.name}")
            }
        } as CursorApi
        return AgentRepository(
            api = api,
            sse = SseStreamer(OkHttpClient(), json),
            store = store,
            catalog = catalog,
            publicHttp = OkHttpClient(),
            json = json,
        )
    }

    @Test
    fun a401InvalidKeyBecomesOneClearExpiredSessionAndNothingElseGoesOut() = runBlocking {
        val repo = repo()
        try {
            repo.getAgent("a")
            fail("expected 401")
        } catch (e: ApiException) {
            assertTrue(e.isInvalidKey)
            assertEquals("Your Cursor sign-in expired. Sign in again.", e.displayMessage())
        }
        assertTrue(repo.session.expired())
        assertEquals(1, calls.size)

        repeat(20) {
            for (call in listOf<suspend () -> Any?>({ repo.getAgent("a") }, { repo.getRun("a", "r") }, { repo.me() }, { repo.listAgentsPage() })) {
                try {
                    call()
                    fail("the session is expired, nothing should succeed")
                } catch (e: ApiException) {
                    assertTrue(e.isInvalidKey)
                }
            }
        }
        assertEquals("no request reaches the network with the rejected key", 1, calls.size)

        val live = listOf(AgentSummary(id = "run", status = "RUNNING"), AgentSummary(id = "blank", status = null))
        repo.hydrateStatuses(live)
        repo.resolveAgentRepos(live)
        assertEquals(1, calls.size)
    }

    @Test
    fun signingInAgainWithANewKeyLiftsTheBlock() = runBlocking {
        val repo = repo()
        try {
            repo.me()
        } catch (_: ApiException) {
        }
        assertTrue(repo.session.expired())
        store.apiKey = "key_NEW_SESSION"
        assertFalse(repo.session.expired())
        assertEquals("a@b.c", repo.me().userEmail)
        assertEquals("a", repo.getAgent("a").id)
    }

    @Test
    fun theStatusGateDoesNotTreatA401AsARateLimit() = runBlocking {
        val repo = repo()
        try {
            repo.getAgent("a")
        } catch (_: ApiException) {
        }
        assertFalse(repo.statusGate.isPaused)
    }

    @Test
    fun theStreamEndsWithTheExpiredSessionWithoutOpeningAConnection() = runBlocking {
        val repo = repo()
        try {
            repo.me()
        } catch (_: ApiException) {
        }
        try {
            repo.stream("a", "r").collect { }
            fail("expected the expired session")
        } catch (e: ApiException) {
            assertTrue(e.isInvalidKey)
        }
    }

    @Test
    fun backgroundWorkersStopOnAnInvalidKey() {
        val expired = ApiException(401, "Invalid User API Key")
        assertTrue(RunWatchBackoff.signedOut(expired))
        assertTrue(RunWatchBackoff.signedOut(ApiException.sessionExpired()))
        assertFalse(RunWatchBackoff.signedOut(ApiException(500, "boom")))
        assertFalse(RunWatchBackoff.signedOut(ApiException(429, "slow")))
        assertEquals(emptyList<AgentSummary>(), sweepTargets(emptyList(), { null }))
    }

    @Test
    fun guardOnlyBlocksTheKeyThatFailedAndIgnoresAStaleFailureFromAnOldKey() {
        var current: String? = "k1"
        val guard = SessionGuard { current }
        guard.markExpired("k1")
        assertTrue(guard.expired())
        current = "k2"
        assertFalse(guard.expired())
        guard.markExpired("k1")
        assertFalse("a late 401 for the previous key must not expire the new session", guard.expiredFor("k2"))
        guard.markExpired("k2")
        assertTrue(guard.expired())
        guard.reset()
        assertFalse(guard.expired())
        guard.markExpired(null)
        guard.markExpired("  ")
        assertFalse(guard.expired())
    }

    @Test
    fun apiExceptionRecognisesTheInvalidKeyByCodeOrMessage() {
        assertTrue(ApiException(401, "anything").isInvalidKey)
        assertTrue(ApiException(0, "Invalid User API Key").isInvalidKey)
        assertTrue(ApiException.fromBody(401, """{"error":{"message":"Invalid User API Key"}}""").isInvalidKey)
        assertFalse(ApiException(429, "slow down").isInvalidKey)
        assertFalse(ApiException(500, "boom").isInvalidKey)
    }
}
