package com.cursorandroid.app.data.repo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object AppUpdate {
    val REPO = "Foreverlegion/Cursor-Unofficial-Android"
    const val ACTION_INSTALL = "com.cursorandroid.app.UPDATE_INSTALL"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .addNetworkInterceptor { chain ->
            val host = chain.request().url.host
            if (!SafeLinks.isGithubHost(host)) {
                throw IOException("Unexpected host $host")
            }
            chain.proceed(chain.request())
        }
        .build()

    data class Installed(
        val versionName: String,
        val versionCode: Long,
    )

    data class ApkInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val sameSigner: Boolean,
    )

    data class Remote(
        val versionName: String,
        val versionCode: Long,
        val apkUrl: String?,
        val tag: String?,
    )

    fun installed(context: Context): Installed {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return Installed(info.versionName.orEmpty(), info.longVersionCodeCompat())
    }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    fun requestInstallPermission(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun copyUri(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "update.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { SafeLinks.copyBounded(input, it, MAX_APK_BYTES) }
        } ?: error("Could not read APK")
        return out
    }

    fun inspect(context: Context, apk: File): ApkInfo {
        val flags = signerFlags()
        val parsed = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Not a valid APK")
        parsed.applicationInfo?.sourceDir = apk.absolutePath
        parsed.applicationInfo?.publicSourceDir = apk.absolutePath
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        return ApkInfo(
            packageName = parsed.packageName.orEmpty(),
            versionName = parsed.versionName.orEmpty(),
            versionCode = parsed.longVersionCodeCompat(),
            sameSigner = sameSigner(installed, parsed),
        )
    }

    fun install(context: Context, apk: File) {
        runCatching { installSession(context, apk) }.getOrElse { installViaView(context, apk) }
    }

    private fun installSession(context: Context, apk: File) {
        val app = context.applicationContext
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(app, AppUpdateReceiver::class.java).setAction(ACTION_INSTALL)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            session.commit(
                PendingIntent.getBroadcast(app, sessionId, callback, flags).intentSender,
            )
        } catch (err: Exception) {
            session.abandon()
            throw err
        }
    }

    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    private fun installViaView(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }

    fun checkReady(context: Context, apk: File): String? {
        val here = installed(context)
        val info = inspect(context, apk)
        return when {
            info.packageName != context.packageName -> "APK is ${info.packageName}, not this app"
            info.versionCode < here.versionCode ->
                "APK ${info.versionName} is older than ${here.versionName}"
            !info.sameSigner ->
                "APK ${info.versionName} is signed with a different key. Uninstall this app, then install the APK."
            else -> null
        }
    }

    suspend fun findRemote(token: String? = null): Remote {
        val published = latestPublished(token)
        val gradle = runCatching { readGradleVersion(token) }.getOrNull()
        return when {
            published != null && gradle != null ->
                if (published.versionCode >= gradle.versionCode) {
                    published
                } else {
                    gradle.copy(apkUrl = published.apkUrl ?: gradle.apkUrl, tag = published.tag)
                }
            published != null -> published
            gradle != null -> gradle
            else -> error("Could not read the latest version from GitHub")
        }
    }

    private fun readGradleVersion(token: String?): Remote {
        val source = readMainGradle(token)
        val versionCode = Regex("""versionCode\s*=\s*(\d+)""").find(source)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("repo has no versionCode")
        val versionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(source)?.groupValues?.get(1)
            ?: error("repo has no versionName")
        return Remote(versionName, versionCode, null, null)
    }

    suspend fun applyIfAvailable(context: Context, token: String? = null): Boolean {
        val remote = findRemote(token)
        val here = installed(context)
        val url = remote.apkUrl ?: return false
        if (remote.versionCode <= here.versionCode) return false
        if (!canInstall(context)) return false
        val apk = download(context, url, token)
        if (checkReady(context, apk) != null) return false
        install(context, apk)
        return true
    }

    suspend fun download(context: Context, url: String, token: String? = null): File {
        if (SafeLinks.githubHttps(url) == null) error("APK URL is not GitHub HTTPS")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "update.apk")
        val accept = if (url.contains("/releases/assets/")) {
            "application/octet-stream"
        } else {
            "application/vnd.android.package-archive"
        }
        val response = http.newCall(request(url, token, accept)).execute()
        if (!response.isSuccessful) error("Download ${response.code}")
        val declared = response.body?.contentLength() ?: -1L
        if (declared > MAX_APK_BYTES) error("APK is too large")
        response.body?.byteStream()?.use { input ->
            out.outputStream().use { SafeLinks.copyBounded(input, it, MAX_APK_BYTES) }
        } ?: error("Empty APK download")
        return out
    }

    private fun readMainGradle(token: String?): String {
        val refs = linkedSetOf(defaultBranch(token), "Main", "main").filter { it.isNotBlank() }
        var last: Throwable? = null
        for (ref in refs) {
            val api = runCatching {
                decodeContents(get("https://api.github.com/repos/$REPO/contents/app/build.gradle.kts?ref=$ref", token))
            }.getOrNull()
            if (!api.isNullOrBlank()) return api
            val raw = runCatching {
                get("https://raw.githubusercontent.com/$REPO/$ref/app/build.gradle.kts", token)
            }
            raw.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            last = raw.exceptionOrNull()
        }
        error(last?.message?.takeIf { it.isNotBlank() } ?: httpError(404, token))
    }

    private fun defaultBranch(token: String?): String {
        val body = runCatching { get("https://api.github.com/repos/$REPO", token) }.getOrNull() ?: return ""
        return runCatching { json.decodeFromString<GhRepo>(body).default_branch }.getOrNull().orEmpty()
    }

    private fun latestPublished(token: String?): Remote? {
        val body = runCatching {
            get("https://api.github.com/repos/$REPO/releases?per_page=5", token)
        }.getOrNull() ?: return null
        val release = json.decodeFromString<List<GhRelease>>(body)
            .firstOrNull { release ->
                !release.draft &&
                    release.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
            } ?: return null
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return null
        val apkUrl = if (!token.isNullOrBlank() && !asset.url.isNullOrBlank()) {
            asset.url
        } else {
            asset.browser_download_url
        }
        val title = release.name.orEmpty()
        val tag = release.tag_name?.removePrefix("v").orEmpty()
        val versionCode = Regex("""\((\d+)\)""").find(title)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val versionName = Regex("""(\d+\.\d+\.\d+)""").find(title)?.groupValues?.get(1)
            ?: tag.takeIf { it.isNotBlank() }
            ?: return null
        return Remote(versionName, versionCode, apkUrl, release.tag_name)
    }

    private fun decodeContents(raw: String): String {
        val payload = json.decodeFromString<GhContents>(raw)
        val b64 = payload.content.orEmpty().replace("\n", "")
        if (b64.isBlank()) error("empty contents")
        return String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
    }

    private fun get(url: String, token: String? = null): String {
        val response = http.newCall(request(url, token, "application/vnd.github+json")).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) error(httpError(response.code, token))
        return body
    }

    private fun request(url: String, token: String?, accept: String): Request {
        if (SafeLinks.githubHttps(url) == null) error("Not a GitHub HTTPS URL")
        return Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", ClientOrigin.ID)
            .apply {
                if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            }
            .build()
    }

    private fun httpError(code: Int, token: String?): String {
        if (code == 404 || code == 403) {
            return if (token.isNullOrBlank()) {
                "Could not read the repo. If it is private, add a GitHub token."
            } else {
                "GitHub token could not read the repo (HTTP $code)."
            }
        }
        return "GitHub HTTP $code"
    }

    @Suppress("DEPRECATION")
    private fun signerFlags(): Int {
        return if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(installed: PackageInfo, apk: PackageInfo): Boolean {
        val left = signerSet(installed)
        val right = signerSet(apk)
        return left.isNotEmpty() && left == right
    }

    @Suppress("DEPRECATION")
    private fun signerSet(info: PackageInfo): Set<String> {
        return if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
                ?.map { it.toCharsString() }
                ?.toSet()
                .orEmpty()
        } else {
            info.signatures
                ?.map { it.toCharsString() }
                ?.toSet()
                .orEmpty()
        }
    }

    @Serializable
    private data class GhRepo(
        val default_branch: String? = null,
    )

    @Serializable
    private data class GhRelease(
        val draft: Boolean = false,
        val tag_name: String? = null,
        val name: String? = null,
        val assets: List<GhAsset> = emptyList(),
    )

    @Serializable
    private data class GhContents(
        val content: String? = null,
        val encoding: String? = null,
    )

    @Serializable
    private data class GhAsset(
        val name: String,
        val url: String? = null,
        val browser_download_url: String? = null,
    )

    private const val MAX_APK_BYTES = 100L * 1024L * 1024L
}

class AppUpdateReceiver : BroadcastReceiver() {
    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            } ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
        }
    }
}

private fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= 28) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()
