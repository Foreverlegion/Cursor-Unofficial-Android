package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.AgentListResponse
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.CreateRunResponse
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.auth.ApiKeyStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class CloudApiRepoTest {
    private lateinit var context: Context
    private lateinit var ui: UiPrefsStore
    private lateinit var catalog: CatalogCache
    private val calls = ArrayList<String>()
    private var seenPrUrl: String? = "unset"
    private var cancelError: ApiException? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs", "catalog")
            .forEach { context.deleteSharedPreferences(it) }
        ui = UiPrefsStore.open(context)
        catalog = CatalogCache(context)
        calls.clear()
        seenPrUrl = "unset"
        cancelError = null
    }

    @After
    fun tearDown() {
        ui.close()
    }

    @Test
    fun listAgentsPagePassesPrUrlAndOmitsBlank() = runBlocking {
        val repo = repo()
        repo.listAgentsPage(prUrl = " https://github.com/acme/app/pull/4 ")
        assertEquals("https://github.com/acme/app/pull/4", seenPrUrl)
        repo.listAgentsPage(prUrl = "  ")
        assertNull(seenPrUrl)
    }

    @Test
    fun sendQueuedNowCancelsThenCreatesARun() = runBlocking {
        val repo = repo()
        val run = repo.sendQueuedNow("bc-1", "run-1", Prompt("now"))
        assertEquals("run-2", run.id)
        assertEquals(listOf("cancel", "run"), calls)
    }

    @Test
    fun sendQueuedNowContinuesWhenTheRunAlreadyFinished() = runBlocking {
        cancelError = ApiException(409, "run_not_cancellable", "run_not_cancellable")
        val repo = repo()
        val run = repo.sendQueuedNow("bc-1", "run-1", Prompt("now"))
        assertEquals("run-2", run.id)
        assertEquals(listOf("cancel", "run"), calls)
    }

    private fun repo(): AgentRepository {
        val api = Proxy.newProxyInstance(
            CursorApi::class.java.classLoader,
            arrayOf(CursorApi::class.java),
        ) { _, method, args ->
            when (method.name) {
                "listAgents" -> {
                    seenPrUrl = args?.filterIsInstance<String>()?.firstOrNull { it.startsWith("http") }
                    AgentListResponse()
                }
                "cancelRun" -> {
                    calls += "cancel"
                    cancelError?.let { throw http(it) }
                    null
                }
                "createRun" -> {
                    calls += "run"
                    CreateRunResponse(Run(id = "run-2", status = "CREATING"))
                }
                else -> error("unexpected ${method.name}")
            }
        } as CursorApi
        val store = ApiKeyStore(context, ui) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
        return AgentRepository(
            api = api,
            sse = SseStreamer(OkHttpClient(), Json { ignoreUnknownKeys = true }),
            store = store,
            catalog = catalog,
            publicHttp = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    private fun http(error: ApiException): HttpException {
        val body = """{"error":{"code":"${error.errorCode}","message":"${error.message}"}}"""
        val raw = okhttp3.Response.Builder()
            .code(error.code)
            .message("Conflict")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://api.cursor.com/v1/agents/a/runs/r/cancel").build())
            .build()
        return HttpException(Response.error<Any>(body.toResponseBody("application/json".toMediaType()), raw))
    }
}
