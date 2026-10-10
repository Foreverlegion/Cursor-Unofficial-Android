package com.cursorandroid.app.data.repo

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

const val MCP_TEMPLATE_NAME = "mcp-template.json"

/**
 * Three dummy servers in the format the importer reads. All are disabled and use placeholder values,
 * so importing the file as it is saves three switched-off entries and nothing is attached to an agent.
 */
fun mcpTemplateJson(): String = """
{
  "mcpServers": {
    "example-http": {
      "url": "https://example.com/mcp",
      "headers": {
        "Authorization": "Bearer YOUR_TOKEN_HERE"
      },
      "enabled": false
    },
    "example-sse": {
      "type": "sse",
      "url": "https://example.com/sse",
      "enabled": false
    },
    "example-stdio": {
      "command": "npx",
      "args": ["-y", "@example/mcp-server"],
      "env": {
        "API_KEY": "YOUR_TOKEN_HERE"
      },
      "enabled": false
    }
  }
}
""".trimStart()

/** `mcp-template.json`, then `mcp-template (1).json`, `mcp-template (2).json`, ... skipping [taken]. */
fun nextTemplateName(taken: Collection<String>): String {
    val used = taken.map { it.lowercase() }.toSet()
    if (MCP_TEMPLATE_NAME !in used) return MCP_TEMPLATE_NAME
    val stem = MCP_TEMPLATE_NAME.removeSuffix(".json")
    var n = 1
    while ("$stem ($n).json" in used) n++
    return "$stem ($n).json"
}

class McpTemplateSaved(val uri: Uri, val displayName: String)

object McpTemplateFile {
    /** True when the public Downloads folder can be written without a permission (MediaStore, API 29+). */
    val directWrite: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Writes the template to the public Downloads folder through MediaStore. Never overwrites: a name
     * that is already there gets ` (1)`, ` (2)` and so on. Returns null when the file could not be made.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveToDownloads(resolver: ContentResolver, body: String = mcpTemplateJson()): McpTemplateSaved? {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val name = nextTemplateName(existingNames(resolver, collection))
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(body.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        runCatching { resolver.update(uri, done, null, null) }
        val shown = runCatching {
            resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: name
        return McpTemplateSaved(uri, shown)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun existingNames(resolver: ContentResolver, collection: Uri): List<String> {
        val out = ArrayList<String>()
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("mcp-template%"),
                null,
            )?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let(out::add)
            }
        }
        return out
    }

    /** Opens the saved file in a viewer, then falls back to the Downloads app. */
    fun open(context: Context, uri: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/json")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val asText = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "text/plain")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val downloads = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return listOf(view, asText, downloads).any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }
    }
}
