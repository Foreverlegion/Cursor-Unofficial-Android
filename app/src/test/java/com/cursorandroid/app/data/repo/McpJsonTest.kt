package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpJsonTest {
    private val desktop = """
        {
          "mcpServers": {
            "linear": {
              "url": "https://mcp.linear.app/mcp",
              "headers": { "Authorization": "Bearer lin_secret" }
            },
            "legacy-sse": { "url": "https://old.example/sse", "type": "sse" },
            "fs": {
              "command": "npx",
              "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
              "env": { "TOKEN": "abc", "FROM_SHELL": "${'$'}{env:HOME}" }
            },
            "oauth": {
              "url": "https://mcp.example/mcp",
              "auth": { "CLIENT_ID": "cid", "CLIENT_SECRET": "sec", "scopes": ["read", "write"] }
            },
            "off": { "url": "https://off.example/mcp", "disabled": true },
            "plain": { "url": "http://insecure.example/mcp" },
            "empty": { "args": ["x"] }
          }
        }
    """.trimIndent()

    private fun parse() = parseMcpJson(desktop).items.associateBy { it.server.name }

    @Test
    fun readsCursorDesktopFormat() {
        val items = parse()
        assertEquals(7, items.size)
        val linear = items.getValue("linear")
        assertNull(linear.problem)
        assertEquals(TYPE_HTTP, linear.server.type)
        assertEquals("Bearer lin_secret", linear.server.headers["Authorization"])

        val fs = items.getValue("fs").server
        assertEquals(TYPE_STDIO, fs.type)
        assertEquals("npx", fs.command)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-filesystem", "/tmp"), fs.args)
        assertEquals("abc", fs.env["TOKEN"])
        assertTrue(items.getValue("fs").notes.any { it.contains("variables") })

        val oauth = items.getValue("oauth").server
        assertEquals("cid", oauth.auth?.clientId)
        assertEquals("sec", oauth.auth?.clientSecret)
        assertEquals(listOf("read", "write"), oauth.auth?.scopes)
        assertEquals("cid", oauth.toApi()?.auth?.clientId)
    }

    @Test
    fun sseAndDisabledAreKept() {
        val items = parse()
        assertEquals(TYPE_SSE, items.getValue("legacy-sse").server.type)
        assertEquals("sse", items.getValue("legacy-sse").server.toApi()?.type)
        assertTrue(items.getValue("legacy-sse").notes.any { it.contains("SSE") })
        assertFalse(items.getValue("off").server.enabled)
    }

    @Test
    fun unusableEntriesAreFlaggedNotDropped() {
        val items = parse()
        assertEquals("Needs an https URL", items.getValue("plain").problem)
        assertNotNull(items.getValue("empty").problem)
    }

    @Test
    fun acceptsABareMapAndTheServersKey() {
        val bare = parseMcpJson("""{"a":{"url":"https://a.example/mcp"}}""")
        assertEquals(listOf("a"), bare.items.map { it.server.name })
        val vscode = parseMcpJson("""{"servers":{"b":{"command":"uvx","args":["pkg"]}}}""")
        assertEquals(TYPE_STDIO, vscode.items.single().server.type)
        val bom = parseMcpJson("\uFEFF" + """{"mcpServers":{"c":{"url":"https://c.example/mcp"}}}""")
        assertNull(bom.error)
    }

    @Test
    fun badInputGivesAnError() {
        assertEquals("Nothing to import", parseMcpJson("  ").error)
        assertEquals("Not valid JSON", parseMcpJson("{nope").error)
        assertEquals("Not valid JSON", parseMcpJson("[1,2]").error)
        assertNotNull(parseMcpJson("""{"theme":"dark"}""").error)
        assertNotNull(parseMcpJson("""{"mcpServers":{}}""").error)
    }

    @Test
    fun exportWithoutSecretsKeepsKeysAndBlanksValues() {
        val items = parse().values.map { it.server }.filter { it.toApi() != null }
        val out = exportMcpJson(items, includeSecrets = false)
        assertFalse(out, out.contains("lin_secret"))
        assertFalse(out, out.contains("\"sec\""))
        assertFalse(out, out.contains("abc"))
        val back = parseMcpJson(out).items.associateBy { it.server.name }
        assertEquals("", back.getValue("linear").server.headers["Authorization"])
        assertEquals("", back.getValue("fs").server.env["TOKEN"])
        assertEquals("cid", back.getValue("oauth").server.auth?.clientId)
        assertEquals("", back.getValue("oauth").server.auth?.clientSecret)
        assertFalse(back.getValue("off").server.enabled)
    }

    @Test
    fun exportWithSecretsRoundTrips() {
        val items = parse().values.map { it.server }.filter { it.toApi() != null }
        val back = parseMcpJson(exportMcpJson(items, includeSecrets = true)).items.map { it.server }
        assertEquals(items.map { it.name }, back.map { it.name })
        val linear = back.single { it.name == "linear" }
        assertEquals("Bearer lin_secret", linear.headers["Authorization"])
        assertEquals("sec", back.single { it.name == "oauth" }.auth?.clientSecret)
        assertEquals(TYPE_SSE, back.single { it.name == "legacy-sse" }.type)
    }

    @Test
    fun mergeAddsNewAndAsksBeforeReplacing() {
        val saved = StoredMcpServer(
            name = "Linear",
            url = "https://old.example/mcp",
            headers = mapOf("Authorization" to "Bearer keepme"),
        )
        val incoming = parseMcpJson(exportMcpJson(listOf(StoredMcpServer(name = "linear", url = "https://new.example/mcp", headers = mapOf("Authorization" to "Bearer other")), StoredMcpServer(name = "docs", url = "https://docs.example/mcp")), true)).items.map { it.server }
        assertEquals(listOf("linear"), mcpConflicts(listOf(saved), incoming).map { it.name })

        val kept = mergeMcpImport(listOf(saved), incoming, replaceNames = emptySet())
        assertEquals(1, kept.added)
        assertEquals(1, kept.skipped)
        assertEquals("https://old.example/mcp", kept.items.first { it.name == "Linear" }.url)

        val replaced = mergeMcpImport(listOf(saved), incoming, replaceNames = setOf("linear"))
        assertEquals(1, replaced.replaced)
        val linear = replaced.items.first { mcpNameKey(it.name) == "linear" }
        assertEquals(saved.id, linear.id)
        assertEquals("https://new.example/mcp", linear.url)
    }

    @Test
    fun replacingFromASecretlessFileKeepsSavedSecrets() {
        val saved = StoredMcpServer(
            name = "linear",
            url = "https://old.example/mcp",
            headers = mapOf("Authorization" to "Bearer keepme"),
            auth = StoredMcpAuth(clientId = "cid", clientSecret = "keepsecret"),
        )
        val file = exportMcpJson(listOf(saved.copy(url = "https://new.example/mcp")), includeSecrets = false)
        val incoming = parseMcpJson(file).items.map { it.server }
        val merged = mergeMcpImport(listOf(saved), incoming, setOf("linear")).items.single()
        assertEquals("https://new.example/mcp", merged.url)
        assertEquals("Bearer keepme", merged.headers["Authorization"])
        assertEquals("keepsecret", merged.auth?.clientSecret)
    }

    @Test
    fun mergeStopsAtFiftyServers() {
        val saved = (1..50).map { StoredMcpServer(name = "s$it", url = "https://s$it.example/mcp") }
        val merged = mergeMcpImport(saved, listOf(StoredMcpServer(name = "extra", url = "https://x.example/mcp")), emptySet())
        assertEquals(50, merged.items.size)
        assertEquals(1, merged.skipped)
    }

    @Test
    fun presetsPrefillNameAndUrlAndLeaveTheTokenBlank() {
        val github = MCP_PRESETS.single { it.label == "GitHub" }.toServer()
        assertEquals("https://api.githubcopilot.com/mcp/", github.url)
        assertEquals("Bearer ", github.headers["Authorization"])
        assertNull("a bare Bearer is never sent", github.toApi()?.headers)
        assertEquals(
            listOf(
                "https://api.githubcopilot.com/mcp/",
                "https://mcp.context7.com/mcp",
                "https://mcp.cloudflare.com/mcp",
                "https://docs.mcp.cloudflare.com/mcp",
                "https://mcp.postman.com/minimal",
                "https://gitlab.com/api/v4/mcp",
            ),
            MCP_PRESETS.map { it.url },
        )
        assertTrue(MCP_PRESETS.single { it.label == "GitLab" }.oauthOnly)
        assertTrue(MCP_PRESETS.all { it.toServer().toApi() != null })
        assertTrue(isOAuthOnlyHost("https://gitlab.com/api/v4/mcp"))
        assertFalse(isOAuthOnlyHost("https://mcp.context7.com/mcp"))
    }

    @Test
    fun authSerializesWithTheApiFieldNames() {
        val api = StoredMcpServer(
            name = "x",
            url = "https://x.example/mcp",
            auth = StoredMcpAuth(clientId = "cid", clientSecret = "", scopes = listOf("a")),
        ).toApi()
        val wire = kotlinx.serialization.json.Json { explicitNulls = false }
            .encodeToString(com.cursorandroid.app.data.api.McpServer.serializer(), api!!)
        assertTrue(wire, wire.contains("\"CLIENT_ID\":\"cid\""))
        assertFalse(wire, wire.contains("CLIENT_SECRET"))
        assertTrue(wire, wire.contains("\"scopes\":[\"a\"]"))
        val stdio = StoredMcpServer(name = "s", type = TYPE_STDIO, command = "npx", auth = StoredMcpAuth(clientId = "cid")).toApi()
        assertNull(stdio?.auth)
    }
}
