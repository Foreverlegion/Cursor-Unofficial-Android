package com.cursorandroid.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.cursorandroid.app.data.api.ApiShape
import com.cursorandroid.app.data.api.CancelLog
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.auth.SessionGuard
import com.cursorandroid.app.data.auth.CursorBrowserLogin
import com.cursorandroid.app.data.notify.NoticeStore
import com.cursorandroid.app.data.notify.RunNotifier
import com.cursorandroid.app.data.repo.AgentRepository
import com.cursorandroid.app.data.repo.ArchiveCoordinator
import com.cursorandroid.app.data.repo.InboxSignals
import com.cursorandroid.app.data.repo.LocalFolderStore
import com.cursorandroid.app.data.repo.PendingArchiveStore
import com.cursorandroid.app.data.repo.ArtifactHistoryStore
import com.cursorandroid.app.data.repo.StagedRestore
import com.cursorandroid.app.data.repo.StatusCallMeter
import com.cursorandroid.app.data.repo.StatusGate
import com.cursorandroid.app.data.repo.CatalogCache
import com.cursorandroid.app.data.repo.ConversationStore
import com.cursorandroid.app.data.repo.DemoCursorApi
import com.cursorandroid.app.data.repo.DemoSession
import com.cursorandroid.app.data.repo.DraftStore
import com.cursorandroid.app.data.repo.FeedbackStore
import com.cursorandroid.app.data.repo.ForgeSyncStore
import com.cursorandroid.app.data.repo.ForgeSyncer
import com.cursorandroid.app.data.repo.InboxRefreshHub
import com.cursorandroid.app.data.repo.LocalChatStore
import com.cursorandroid.app.data.repo.RunModelStore
import com.cursorandroid.app.data.repo.RunSettleHub
import com.cursorandroid.app.data.repo.MachineStore
import com.cursorandroid.app.data.repo.UiPrefsStore
import com.cursorandroid.app.data.repo.settleAgents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.io.File
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val ui = UiPrefsStore.open(context)
    val store = ApiKeyStore(context, ui)
    val conversations = ConversationStore(context)
    val chats = LocalChatStore(ui)
    val machines = MachineStore(ui)
    val drafts = DraftStore(context)
    val catalog = CatalogCache(context) { store.demoMode }
    val artifactHistory = ArtifactHistoryStore(context)
    val runModels = RunModelStore(context)
    val notices = NoticeStore(context)
    val inboxRefresh = InboxRefreshHub()
    val runSettle = RunSettleHub { ended ->
        conversations.settle(ended.agentId, ended.runId, ended.status)
        val agents = catalog.agents()
        val settled = settleAgents(agents, mapOf(ended.agentId to ended))
        if (settled !== agents) catalog.saveAgents(settled)
    }
    val notifier = RunNotifier(context.applicationContext, store, notices, chats, runSettle, inboxRefresh)
    val feedback = FeedbackStore(context)
    val stagedRestore = StagedRestore(File(context.applicationContext.filesDir, "staged-restore.json"))
    val session = SessionGuard { store.apiKey }
    val forgeSync = ForgeSyncer(
        store = ForgeSyncStore(File(context.applicationContext.filesDir, "forge-sync")),
        forges = { store.forges() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        enabled = { !store.demoMode },
    )

    fun renameChat(agentId: String, name: String) {
        val title = name.trim()
        chats.setTitle(agentId, title)
        notices.relabel(agentId, title)
    }

    fun chatTitles(): Map<String, String> {
        return chats.snapshot().mapNotNull { (id, meta) ->
            meta.title?.takeIf { it.isNotBlank() }?.let { id to it }
        }.toMap()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val authInterceptor = Interceptor { chain ->
        val key = store.apiKey
        val request = if (store.demoMode || key.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .build()
        }
        chain.proceed(request)
    }

    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .apply {
            if (debug) {
                addInterceptor(ApiShape.interceptor(json))
                addInterceptor(CancelLog.interceptor())
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .build()

    fun forgetLocal(agentId: String) {
        conversations.remove(agentId)
        chats.remove(agentId)
        drafts.clear(agentId)
        drafts.clearQueue(agentId)
        artifactHistory.remove(agentId)
        runModels.remove(agentId)
        notices.dismissAgent(agentId)
        catalog.removeGit(agentId)
    }

    private val publicHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val liveApi: CursorApi = Retrofit.Builder()
        .baseUrl("https://api.cursor.com/")
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CursorApi::class.java)

    private val api: CursorApi = DemoCursorApi(store, DemoSession(), liveApi)

    val login = CursorBrowserLogin(publicHttp, json)

    val statusGate = StatusGate(
        meter = StatusCallMeter(sink = if (debug) { line -> Log.d("StatusCalls", line) } else null),
    )

    val repo = AgentRepository(
        api = api,
        sse = SseStreamer(
            http,
            json,
            probe = if (debug) { type, data -> ApiShape.log("SSE $type", data, json) } else null,
            onUnauthorized = { key -> session.markExpired(key) },
        ),
        store = store,
        catalog = catalog,
        publicHttp = publicHttp,
        json = json,
        machines = machines,
        statusGate = statusGate,
        session = session,
    )

    /** Debug builds only: how many status calls went out in the last minute, for the About page. */
    val statusCallsPerMinute: Int? get() = if (debug) statusGate.meter.lastMinute() else null

    val signals = InboxSignals()
    val folders = LocalFolderStore(ui)
    val archives = ArchiveCoordinator(
        store = PendingArchiveStore(ui),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        catalog = catalog,
        archive = { id -> repo.archive(id) },
        signals = signals,
    )
}
