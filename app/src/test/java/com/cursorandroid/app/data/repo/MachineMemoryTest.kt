package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.Env
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.api.Worker
import com.cursorandroid.app.data.api.WorkerListResponse
import com.cursorandroid.app.data.auth.ApiKeyStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MachineMemoryTest {
    private lateinit var context: Context
    private lateinit var ui: UiPrefsStore
    private lateinit var catalog: CatalogCache
    private lateinit var machines: MachineStore
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
    private var clock = 1_800_000_000_000L
    private var pages: List<WorkerListResponse> = emptyList()
    private var workerCalls = 0

    private val combined = Worker(
        workerId = "w-cua",
        name = "DevTop: Cursor-Unofficial-Android",
        repoUrl = "https://github.com/Foreverlegion/Cursor-Unofficial-Android",
        workspaceRootPath = "/home/dev/Cursor-Unofficial-Android",
    )
    private val devTop = Worker(workerId = "w-dt", name = "DevTop")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs", "local_chats", "catalog")
            .forEach { context.deleteSharedPreferences(it) }
        ui = UiPrefsStore.open(context)
        catalog = CatalogCache(context)
        machines = MachineStore(ui) { clock }
    }

    @After
    fun tearDown() {
        ui.close()
    }

    private fun repo(): AgentRepository {
        val api = Proxy.newProxyInstance(CursorApi::class.java.classLoader, arrayOf(CursorApi::class.java)) { _, method, args ->
            check(method.name == "listWorkers") { "unexpected call ${method.name}" }
            val token = args[3] as String?
            workerCalls++
            pages.getOrNull(token?.toInt() ?: 0) ?: WorkerListResponse()
        } as CursorApi
        val store = ApiKeyStore(context, ui) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
        return AgentRepository(
            api = api,
            sse = SseStreamer(OkHttpClient(), json),
            store = store,
            catalog = catalog,
            publicHttp = OkHttpClient(),
            json = json,
            machines = machines,
        )
    }

    private fun page(vararg workers: Worker, next: String? = null) =
        WorkerListResponse(workers = workers.toList(), nextPageToken = next)

    @Test
    fun aWorkerThatDisconnectsStaysListedAsOffline() = runBlocking {
        val repo = repo()
        pages = listOf(page(devTop, combined))
        val first = repo.listComputers()
        assertTrue(first.all { it.online })

        pages = listOf(page(devTop))
        val second = repo.listComputers()
        val gone = second.single { it.name == "DevTop: Cursor-Unofficial-Android" }
        assertFalse(gone.online)
        assertEquals("w-cua", gone.workerId)
        assertEquals("https://github.com/Foreverlegion/Cursor-Unofficial-Android", gone.repoUrl)
        assertEquals("/home/dev/Cursor-Unofficial-Android", gone.workspaceRootPath)
    }

    @Test
    fun anEmptyOrFailedWorkerListDoesNotEmptyTheMachineList() = runBlocking {
        val repo = repo()
        pages = listOf(page(devTop, combined))
        repo.listComputers()
        pages = listOf(page())
        val after = repo.listComputers()
        assertEquals(setOf("DevTop", "DevTop: Cursor-Unofficial-Android"), after.map { it.name }.toSet())
        assertTrue(after.none { it.online })
    }

    @Test
    fun onlyHidingRemovesItFromTheVisibleList() = runBlocking {
        val repo = repo()
        pages = listOf(page(devTop, combined))
        repo.listComputers()
        pages = listOf(page(devTop))
        val listed = repo.listComputers()
        assertEquals(2, machines.visible(listed).size)
        val gone = listed.single { !it.online }
        machines.hide(gone.machineKey(), gone.name)
        assertEquals(listOf("DevTop"), machines.visible(repo.listComputers()).map { it.name })
    }

    @Test
    fun theCachedListFromAnEarlierBuildIsMergedIn() = runBlocking {
        catalog.saveComputers(
            listOf(
                Computer(name = "DevTop: Cursor-Unofficial-Android", online = true, workerId = "w-old", detail = "/home/dev/x"),
                Computer(name = "zenbook", online = false, detail = SEEN_ON_AGENT),
            ),
        )
        pages = listOf(page(devTop))
        val listed = repo().listComputers()
        assertEquals(setOf("DevTop", "DevTop: Cursor-Unofficial-Android", "zenbook"), listed.map { it.name }.toSet())
        val old = listed.single { it.name.startsWith("DevTop:") }
        assertFalse(old.online)
        assertEquals("/home/dev/x", old.detail)
        assertTrue(machines.prefs().seen.containsKey("id:w-old"))
    }

    @Test
    fun aRestartedWorkerUnderANewIdIsNotListedTwice() = runBlocking {
        val repo = repo()
        pages = listOf(page(combined))
        repo.listComputers()
        pages = listOf(page(combined.copy(workerId = "w-new")))
        val listed = repo.listComputers()
        assertEquals(listOf("w-new"), listed.map { it.workerId })
        assertTrue(machines.prefs().seen.containsKey("id:w-cua"))
    }

    @Test
    fun everyWorkerPageIsRead() = runBlocking {
        pages = listOf(page(devTop, next = "1"), page(combined))
        val listed = repo().listComputers()
        assertEquals(2, listed.size)
        assertEquals(2, workerCalls)
    }

    @Test
    fun anAgentEnvironmentNameIsStillListedWhenNoWorkerHasIt() = runBlocking {
        pages = listOf(page(devTop))
        val agents = listOf(AgentSummary(id = "bc-1", env = Env(type = "machine", name = "DevTop: Cursor-Unofficial-Android")))
        val listed = repo().listComputers(agents)
        val off = listed.single { !it.online }
        assertEquals(SEEN_ON_AGENT, off.detail)
    }
}
