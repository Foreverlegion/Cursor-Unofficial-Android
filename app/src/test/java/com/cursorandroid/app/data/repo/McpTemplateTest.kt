package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class McpTemplateTest {
    @Test
    fun templateRoundTripsThroughTheImporter() {
        val parsed = parseMcpJson(mcpTemplateJson())
        assertNull(parsed.error)
        assertEquals(listOf("example-http", "example-sse", "example-stdio"), parsed.items.map { it.server.name })
        assertTrue(parsed.items.all { it.problem == null })
        assertEquals(listOf(TYPE_HTTP, TYPE_SSE, TYPE_STDIO), parsed.items.map { it.server.type })
    }

    @Test
    fun templateServersCarryTheFormatFields() {
        val (http, sse, stdio) = parseMcpJson(mcpTemplateJson()).items.map { it.server }
        assertEquals("https://example.com/mcp", http.url)
        assertEquals(mapOf("Authorization" to "Bearer YOUR_TOKEN_HERE"), http.headers)
        assertEquals("https://example.com/sse", sse.url)
        assertEquals("npx", stdio.command)
        assertEquals(listOf("-y", "@example/mcp-server"), stdio.args)
        assertEquals(mapOf("API_KEY" to "YOUR_TOKEN_HERE"), stdio.env)
    }

    @Test
    fun templateIsAllPlaceholdersAndAllDisabled() {
        val parsed = parseMcpJson(mcpTemplateJson())
        assertTrue(parsed.items.all { !it.server.enabled })
        val text = mcpTemplateJson()
        assertEquals(2, "YOUR_TOKEN_HERE".toRegex().findAll(text).count())
        assertTrue(parsed.items.mapNotNull { it.server.url }.all { it.startsWith("https://example.com/") })
    }

    @Test
    fun importingTheTemplateAsIsAttachesNothingToAnAgent() {
        val parsed = parseMcpJson(mcpTemplateJson())
        val merged = mergeMcpImport(emptyList(), parsed.items.map { it.server }, emptySet())
        assertEquals(3, merged.added)
        assertEquals(3, merged.items.size)
        assertTrue(merged.items.none { it.enabled })
        assertTrue(storedMcpsToApi(merged.items).isNullOrEmpty())
    }

    @Test
    fun importingTheTemplateTwiceSkipsTheSecondCopy() {
        val servers = parseMcpJson(mcpTemplateJson()).items.map { it.server }
        val first = mergeMcpImport(emptyList(), servers, emptySet())
        val second = mergeMcpImport(first.items, servers, emptySet())
        assertEquals(0, second.added)
        assertEquals(3, second.skipped)
        assertEquals(3, second.items.size)
    }

    @Test
    fun enabledFalseAndDisabledTrueBothSwitchAServerOff() {
        val raw = """
            {"mcpServers": {
              "a": {"url": "https://example.com/a", "enabled": false},
              "b": {"url": "https://example.com/b", "disabled": true},
              "c": {"url": "https://example.com/c", "enabled": true},
              "d": {"url": "https://example.com/d"}
            }}
        """.trimIndent()
        val on = parseMcpJson(raw).items.associate { it.server.name to it.server.enabled }
        assertEquals(mapOf("a" to false, "b" to false, "c" to true, "d" to true), on)
    }

    @Test
    fun exportingTheImportedTemplateReadsBackTheSame() {
        val servers = parseMcpJson(mcpTemplateJson()).items.map { it.server }
        val again = parseMcpJson(exportMcpJson(servers, includeSecrets = true)).items.map { it.server }
        assertEquals(servers.map { it.name }, again.map { it.name })
        assertEquals(servers.map { it.type }, again.map { it.type })
        assertEquals(servers.map { it.headers }, again.map { it.headers })
        assertTrue(again.none { it.enabled })
    }

    @Test
    fun nameSkipsOneThatAlreadyExists() {
        assertEquals("mcp-template.json", nextTemplateName(emptyList()))
        assertEquals("mcp-template.json", nextTemplateName(listOf("other.json")))
        assertEquals("mcp-template (1).json", nextTemplateName(listOf("mcp-template.json")))
        assertEquals("mcp-template (2).json", nextTemplateName(listOf("mcp-template.json", "mcp-template (1).json")))
        assertEquals("mcp-template (1).json", nextTemplateName(listOf("MCP-Template.JSON")))
        assertEquals("mcp-template.json", nextTemplateName(listOf("mcp-template (1).json")))
    }

    @Test
    fun writesToDownloadsWithoutOverwriting() {
        val provider = Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver

        val first = McpTemplateFile.saveToDownloads(resolver)
        assertNotNull(first)
        assertEquals("mcp-template.json", first!!.displayName)
        assertEquals(mcpTemplateJson(), provider.read(first.uri))

        val second = McpTemplateFile.saveToDownloads(resolver)
        assertNotNull(second)
        assertEquals("mcp-template (1).json", second!!.displayName)
        assertEquals(mcpTemplateJson(), provider.read(second.uri))
        assertEquals(mcpTemplateJson(), provider.read(first.uri))

        assertEquals(listOf("mcp-template.json", "mcp-template (1).json"), provider.names())
        assertEquals("Download/", provider.relativePath(first.uri))
        assertEquals("application/json", provider.mime(first.uri))
        assertFalse(provider.pending(first.uri))
        assertTrue(parseMcpJson(provider.read(second.uri)!!).items.size == 3)
    }

    @Test
    fun aFailedInsertReturnsNull() {
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media").failInsert = true
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        assertNull(McpTemplateFile.saveToDownloads(resolver))
    }

    class FakeMediaProvider : ContentProvider() {
        private class Row(val id: Long, var values: ContentValues, val file: File)

        private val rows = ArrayList<Row>()
        var failInsert = false

        override fun onCreate(): Boolean = true

        private fun rowFor(uri: Uri): Row? = rows.firstOrNull { it.id == uri.lastPathSegment?.toLongOrNull() }

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor {
            val cursor = MatrixCursor(arrayOf(MediaStore.Downloads.DISPLAY_NAME))
            val single = rowFor(uri)
            val list = if (single != null) listOf(single) else rows
            list.forEach { cursor.addRow(arrayOf(it.values.getAsString(MediaStore.Downloads.DISPLAY_NAME))) }
            return cursor
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (failInsert || values == null) return null
            val id = rows.size + 1L
            val file = File.createTempFile("media-$id", ".bin", context!!.cacheDir)
            rows += Row(id, ContentValues(values), file)
            return Uri.withAppendedPath(uri, id.toString())
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
            val row = rowFor(uri) ?: return 0
            values?.let { row.values.putAll(it) }
            return 1
        }

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int =
            if (rows.removeAll { it.id == uri.lastPathSegment?.toLongOrNull() }) 1 else 0

        override fun getType(uri: Uri): String? = null

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val row = rowFor(uri) ?: throw java.io.FileNotFoundException(uri.toString())
            return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
        }

        fun read(uri: Uri): String? = rowFor(uri)?.file?.readText()
        fun names(): List<String> = rows.map { it.values.getAsString(MediaStore.Downloads.DISPLAY_NAME) }
        fun relativePath(uri: Uri): String? = rowFor(uri)?.values?.getAsString(MediaStore.Downloads.RELATIVE_PATH)
        fun mime(uri: Uri): String? = rowFor(uri)?.values?.getAsString(MediaStore.Downloads.MIME_TYPE)
        fun pending(uri: Uri): Boolean = rowFor(uri)?.values?.getAsInteger(MediaStore.Downloads.IS_PENDING) == 1
    }
}
