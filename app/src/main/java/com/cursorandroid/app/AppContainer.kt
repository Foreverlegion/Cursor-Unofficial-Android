package com.cursorandroid.app

import android.content.Context
import android.content.pm.ApplicationInfo
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.auth.CursorBrowserLogin
import com.cursorandroid.app.data.notify.NoticeStore
import com.cursorandroid.app.data.notify.RunNotifier
import com.cursorandroid.app.data.repo.AgentRepository
import com.cursorandroid.app.data.repo.ArtifactHistoryStore
import com.cursorandroid.app.data.repo.CatalogCache
import com.cursorandroid.app.data.repo.ConversationStore
import com.cursorandroid.app.data.repo.DraftStore
import com.cursorandroid.app.data.repo.LocalChatStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val store = ApiKeyStore(context)
    val conversations = ConversationStore(context)
    val chats = LocalChatStore(context)
    val drafts = DraftStore(context)
    val catalog = CatalogCache(context)
    val artifactHistory = ArtifactHistoryStore(context)
    val notices = NoticeStore(context)
    val notifier = RunNotifier(context.applicationContext, store, notices, chats)

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
        val request = if (key.isNullOrBlank()) {
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
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .apply {
            if (debug) {
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
        notices.dismissAgent(agentId)
        catalog.removeGit(agentId)
    }

    private val publicHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val api: CursorApi = Retrofit.Builder()
        .baseUrl("https://api.cursor.com/")
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CursorApi::class.java)

    val login = CursorBrowserLogin(publicHttp, json)

    val repo = AgentRepository(
        api = api,
        sse = SseStreamer(http, json),
        store = store,
        catalog = catalog,
        publicHttp = publicHttp,
        json = json,
    )
}
