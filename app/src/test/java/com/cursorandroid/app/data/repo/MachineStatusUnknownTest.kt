package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.Env
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.api.Worker
import com.cursorandroid.app.data.api.WorkerListResponse
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.ui.inbox.computerStatusLine
import kotlinx.coroutines.runBlocking
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
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * The Remote list shows a machine as offline only when a fresh workers call succeeded and did not list it.
 * A failed call (rate limit, expired sign-in, network) must read as unknown, never as offline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MachineStatusUnknownTest {
    private lateinit var context: Context
    private lateinit var ui: UiPrefsStore
    private lateinit var catalog: CatalogCache
    private lateinit var machines: MachineStore
    private lateinit var store: ApiKeyStore
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
    private var clock = 1_800_000_000_000L
    private var pages: List<WorkerListResponse> = emptyList()
    private var failure: (Int) -> Throwable? = { null }
    private var workerCalls = 0

    private val devTop = Worker(workerId = "w-dt", name = "DevTop", workspaceRootPath = "/home/dev")
    private val tnMobile = Worker(workerId = "w-tn", name = "DevTop-TNMobile", workspaceRootPath = "C:\\Users\\dev\\src\\Thermal-Nexus-Mobile")
    private val fleet = Worker(workerId = "w-fw", name = "DevTop-FleetWidget", workspaceRootPath = "C:\\Users\\dev\\src\\Fleet-Widget")
    private val agents = listOf(
        AgentSummary(id = "a1", env = Env(type = "machine", name = "DevTop")),
        AgentSummary(id = "a2", env = Env(type = "machine", name = "zenbook")),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs", "local_chats", "catalog")
            .forEach { context.deleteSharedPreferences(it) }
        ui = UiPrefsStore.open(context)
        catalog = CatalogCache(context)
        machines = MachineStore(ui) { clock }
        store = ApiKeyStore(context, ui) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
        store.apiKey = "key_LIVE"
    }

    @After
    fun tearDown() {
        ui.close()
    }

    private lateinit var repo: AgentRepository

    private fun newRepo(): AgentRepository {
        val api = Proxy.newProxyInstance(CursorApi::class.java.classLoader, arrayOf(CursorApi::class.java)) { _, method, args ->
            check(method.name == "listWorkers") { "unexpected call ${method.name}" }
            val token = args[3] as String?
            val index = token?.toInt() ?: 0
            workerCalls++
            failure(index)?.let { throw it }
            pages.getOrNull(index) ?: WorkerListResponse()
        } as CursorApi
        return AgentRepository(
            api = api,
            sse = SseStreamer(OkHttpClient(), json),
            store = store,
            catalog = catalog,
            publicHttp = OkHttpClient(),
            json = json,
            machines = machines,
        ).also { repo = it }
    }

    private fun page(vararg workers: Worker, next: String? = null) =
        WorkerListResponse(workers = workers.toList(), nextPageToken = next)

    private fun rateLimited() = ApiException(429, "exceeded the rate limit of 6000 requests per hour for list_workers", retryAfterMs = 30_000)

    private fun seedKnownOnline(): AgentRepository {
        val repo = newRepo()
        pages = listOf(page(devTop, tnMobile, fleet))
        val first = runBlocking { repo.listMachines(agents) }
        assertTrue(first.known)
        assertTrue(first.computers.filter { it.name.startsWith("DevTop") }.all { it.online })
        runBlocking { repo.refreshComputers(agents) }
        return repo
    }

    @Test
    fun aRateLimitedWorkersCallLeavesEveryMachineUnknownNotOffline() = runBlocking {
        val repo = seedKnownOnline()
        failure = { rateLimited() }
        val listing = repo.listMachines(agents)
        assertFalse(listing.known)
        assertEquals("rate limited", listing.failure)
        assertEquals(setOf("DevTop", "DevTop-TNMobile", "DevTop-FleetWidget", "zenbook"), listing.computers.map { it.name }.toSet())
        assertTrue("nothing is claimed online or offline", listing.computers.all { it.statusUnknown && !it.online })
        listing.computers.forEach { assertEquals("Status unknown, retrying", computerStatusLine(it).substringBefore(" ·")) }
        assertTrue(listing.computers.none { computerStatusLine(it).startsWith("Offline") })
    }

    @Test
    fun anExpiredSignInIsUnknownAndMakesNoNetworkCall() = runBlocking {
        val repo = seedKnownOnline()
        repo.session.markExpired("key_LIVE")
        val before = workerCalls
        val listing = repo.listMachines(agents)
        assertFalse(listing.known)
        assertEquals("sign-in expired", listing.failure)
        assertEquals(before, workerCalls)
        assertTrue(listing.computers.all { it.statusUnknown })
    }

    @Test
    fun aNetworkFailureIsUnknown() = runBlocking {
        val repo = seedKnownOnline()
        failure = { IOException("timeout") }
        val listing = repo.listMachines(agents)
        assertFalse(listing.known)
        assertEquals("no response", listing.failure)
        assertTrue(listing.computers.all { it.statusUnknown })
    }

    @Test
    fun aPausedStatusGateDoesNotSuppressTheMachinesCall() = runBlocking {
        val repo = newRepo()
        repo.statusGate.onRateLimited(60_000)
        assertTrue(repo.statusGate.isPaused)
        pages = listOf(page(devTop, tnMobile, fleet))
        val before = workerCalls
        val listing = repo.listMachines(agents)
        assertEquals("a fresh call went out", before + 1, workerCalls)
        assertTrue(listing.known)
        assertEquals(setOf("DevTop", "DevTop-TNMobile", "DevTop-FleetWidget"), listing.computers.filter { it.online }.map { it.name }.toSet())
        assertFalse(listing.computers.single { it.name == "DevTop" }.statusUnknown)
    }

    @Test
    fun theMachinesCallIsNeverServedFromACache() = runBlocking {
        val repo = newRepo()
        pages = listOf(page(devTop))
        assertEquals(1, repo.listMachines().computers.count { it.online })
        pages = listOf(page(devTop, tnMobile))
        assertEquals(2, repo.listMachines().computers.count { it.online })
        pages = listOf(page())
        val none = repo.listMachines()
        assertTrue(none.known)
        assertEquals(0, none.computers.count { it.online })
        assertEquals(3, workerCalls)
    }

    @Test
    fun aFailedCallLearnsNothingAndTheNextGoodCallRecovers() = runBlocking {
        val repo = seedKnownOnline()
        val seenBefore = machines.prefs().seen
        clock += 3_600_000
        failure = { rateLimited() }
        repo.listMachines(agents)
        assertEquals("a failed listing does not touch what the phone remembers", seenBefore, machines.prefs().seen)

        failure = { null }
        val ok = repo.listMachines(agents)
        assertTrue(ok.known)
        assertNull(ok.failure)
        assertEquals(setOf("DevTop", "DevTop-TNMobile", "DevTop-FleetWidget"), ok.computers.filter { it.online }.map { it.name }.toSet())
        assertTrue(ok.computers.none { it.statusUnknown })
    }

    @Test
    fun aFailedCallNeverOverwritesTheCachedList() = runBlocking {
        val repo = seedKnownOnline()
        val cached = catalog.computers()
        assertTrue(cached.any { it.online })
        failure = { rateLimited() }
        val shown = repo.refreshComputers(agents)
        assertEquals(cached, shown)
        assertEquals(cached, catalog.computers())
        assertTrue(catalog.computers().none { it.statusUnknown })
    }

    @Test
    fun aListFailingAfterTheFirstPageKeepsThoseOnlineAndMarksTheRestUnknown() = runBlocking {
        val repo = seedKnownOnline()
        pages = listOf(page(devTop, next = "1"), page(tnMobile, fleet))
        failure = { index -> if (index == 1) IOException("dropped") else null }
        val listing = repo.listMachines(agents)
        assertFalse(listing.known)
        assertTrue(listing.computers.single { it.name == "DevTop" }.online)
        assertFalse(listing.computers.single { it.name == "DevTop" }.statusUnknown)
        assertTrue(listing.computers.single { it.name == "DevTop-TNMobile" }.statusUnknown)
        assertTrue(listing.computers.single { it.name == "zenbook" }.statusUnknown)
    }

    @Test
    fun aSuccessfulEmptyListIsStillOffline() = runBlocking {
        val repo = seedKnownOnline()
        pages = listOf(page())
        val listing = repo.listMachines(agents)
        assertTrue(listing.known)
        assertTrue(listing.computers.none { it.online || it.statusUnknown })
        assertEquals("Offline", computerStatusLine(listing.computers.first()).substringBefore(" ·"))
    }

    @Test
    fun settingsMachinesRowsSayStatusUnknownToo() {
        val row = machineRows(
            listOf(Computer(name = "DevTop", online = false, statusUnknown = true)),
            MachinePrefs(),
            clock,
        ).single()
        assertTrue(row.statusUnknown)
        assertTrue(row.statusLine(clock).startsWith("Status unknown"))
    }

    @Test
    fun theInboxRetriesMachinesSoonerAfterAFailedListingAndAlwaysOnRefresh() {
        val every = InboxPollPolicy.MACHINES_EVERY_MS
        val retry = InboxPollPolicy.MACHINES_RETRY_MS
        assertFalse(InboxPollPolicy.machinesDue(RefreshReason.Tick, 0, retry, lastFailed = false))
        assertTrue(InboxPollPolicy.machinesDue(RefreshReason.Tick, 0, retry, lastFailed = true))
        assertFalse(InboxPollPolicy.machinesDue(RefreshReason.Tick, 0, retry - 1, lastFailed = true))
        assertTrue(InboxPollPolicy.machinesDue(RefreshReason.Tick, 0, every, lastFailed = false))
        listOf(RefreshReason.Manual, RefreshReason.Resume, RefreshReason.Start).forEach {
            assertTrue("$it forces a fresh machines call", InboxPollPolicy.machinesDue(it, 1_000, 1_001))
        }
    }
}
