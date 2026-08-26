package com.cursorandroid.app.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

object SafeLinks {
    private val AGENT_ID = Regex("^bc-[A-Za-z0-9_-]{6,80}$")

    fun agentId(raw: String?): String? {
        return raw?.trim()?.takeIf { AGENT_ID.matches(it) }
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

    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    fun open(context: Context, raw: String?): Boolean {
        val uri = httpsUri(raw) ?: return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString())).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) return@runCatching false
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun isGithubHost(host: String?): Boolean {
        val name = host?.trim()?.lowercase().orEmpty()
        if (name.isEmpty()) return false
        return name == "github.com" ||
            name == "api.github.com" ||
            name.endsWith(".github.com") ||
            name.endsWith(".githubusercontent.com")
    }

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
