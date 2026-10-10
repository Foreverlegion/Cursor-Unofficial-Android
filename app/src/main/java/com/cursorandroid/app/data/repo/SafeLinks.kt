package com.cursorandroid.app.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

data class AgentLink(
    val agentId: String?,
    val invalid: Boolean,
)

object SafeLinks {
    private val AGENT_ID = Regex("^bc-[A-Za-z0-9_-]{6,80}$")
    private val AGENT_HOSTS = setOf("cursor.com", "www.cursor.com")

    fun agentId(raw: String?): String? {
        return raw?.trim()?.takeIf { AGENT_ID.matches(it) }
    }

    fun agentLink(raw: String?): AgentLink? {
        val uri = httpsUri(raw) ?: return null
        val host = uri.host?.trim()?.lowercase().orEmpty()
        if (host !in AGENT_HOSTS) return null
        val path = uri.path?.trimEnd('/').orEmpty()
        if (!path.startsWith("/agents/") && path != "/agents") return null
        val rest = path.removePrefix("/agents").removePrefix("/")
        if (rest.isEmpty() || rest.contains('/')) return AgentLink(agentId = null, invalid = true)
        val id = agentId(rest)
        return if (id == null) AgentLink(agentId = null, invalid = true) else AgentLink(id, invalid = false)
    }

    fun httpsUri(raw: String?): URI? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (!uri.userInfo.isNullOrBlank()) return null
        return uri
    }

    fun isHttps(raw: String?): Boolean = httpsUri(raw) != null

    private val PR_PATH = Regex("^(https://[^/\\s]+/\\S+?/(?:pull|pulls|-/merge_requests|pull-requests)/\\d+)(?:[/?#]\\S*)?$")

    /** A lone pull/merge request URL, or null. */
    fun pullRequestUrl(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) } ?: return null
        if (!isHttps(text)) return null
        return PR_PATH.matchEntire(text)?.groupValues?.get(1)
    }

    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    fun openSupportedLinks(context: Context) {
        val uri = Uri.parse("package:${context.packageName}")
        val openByDefault = if (android.os.Build.VERSION.SDK_INT >= 31) {
            Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, uri)
        } else {
            null
        }
        val details = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        val primary = (openByDefault ?: details).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(primary) }.isSuccess) return
        runCatching { context.startActivity(details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun forcesBrowser(raw: String?): Boolean {
        val host = httpsUri(raw)?.host?.trim()?.lowercase().orEmpty()
        return host == "cursor.com" || host == "www.cursor.com"
    }

    fun open(context: Context, raw: String?): Boolean {
        val target = httpsUri(raw) ?: return false
        val uri = Uri.parse(target.toString())
        // cursor.com is in this app's VIEW filter. A non-browser match would be us.
        if (!forcesBrowser(raw) && start(context, appView(uri))) return true
        return openInBrowser(context, uri)
    }

    private fun appView(uri: Uri): Intent {
        val flags = if (android.os.Build.VERSION.SDK_INT >= 30) {
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER
        } else {
            Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return Intent(Intent.ACTION_VIEW, uri).addFlags(flags)
    }

    private fun openInBrowser(context: Context, uri: Uri): Boolean {
        val browser = browserPackage(context)
        if (browser != null) {
            val direct = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(browser)
            }
            if (start(context, direct)) return true
        }
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val selected = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            selector = probe
        }
        if (start(context, selected)) return true
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE),
            null,
        ).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(android.content.ComponentName(context.packageName, LAUNCHER)),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(context, chooser)
    }

    private fun browserPackage(context: Context): String? {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val info = context.packageManager.resolveActivity(
            probe,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        ) ?: return null
        val pkg = info.activityInfo?.packageName ?: return null
        if (pkg == context.packageName || pkg == "android") return null
        if (info.activityInfo?.name.orEmpty().endsWith("ResolverActivity")) return null
        return pkg
    }

    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    private fun start(context: Context, intent: Intent): Boolean {
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun isGithubHost(host: String?): Boolean {
        val name = host?.trim()?.lowercase().orEmpty()
        if (name.isEmpty()) return false
        return name == "github.com" ||
            name == "api.github.com" ||
            name.endsWith(".github.com") ||
            name.endsWith(".githubusercontent.com")
    }

    private const val LAUNCHER = "com.cursorandroid.app.MainActivity"

    fun githubHttps(raw: String?): URI? {
        val uri = httpsUri(raw) ?: return null
        return uri.takeIf { isGithubHost(uri.host) }
    }

    fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) error("File is too large")
            output.write(buf, 0, n)
        }
    }

    fun readBounded(input: InputStream, maxBytes: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        return runCatching {
            copyBounded(input, out, maxBytes)
            out.toByteArray()
        }.getOrNull()
    }
}
