package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigTest {
    @Test
    fun httpNeedsHttpsAndName() {
        assertNull(StoredMcpServer(name = "linear", url = "http://mcp.example").toApi())
        assertNull(StoredMcpServer(name = "", url = "https://mcp.example").toApi())
        val api = StoredMcpServer(
            name = "linear",
            url = "https://mcp.linear.app/sse",
            headers = mapOf("Authorization" to "Bearer x"),
        ).toApi()
        assertEquals("linear", api?.name)
        assertEquals("http", api?.type)
        assertEquals("Bearer x", api?.headers?.get("Authorization"))
    }

    @Test
    fun stdioNeedsCommand() {
        assertNull(StoredMcpServer(name = "gh", type = TYPE_STDIO).toApi())
        val api = StoredMcpServer(
            name = "gh",
            type = TYPE_STDIO,
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-github"),
            env = mapOf("GITHUB_TOKEN" to "gho_x"),
        ).toApi()
        assertEquals("stdio", api?.type)
        assertEquals("npx", api?.command)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-github"), api?.args)
        assertEquals("gho_x", api?.env?.get("GITHUB_TOKEN"))
    }

    @Test
    fun disabledAndDupesAreDropped() {
        val items = listOf(
            StoredMcpServer(name = "a", url = "https://a.example", enabled = false),
            StoredMcpServer(name = "docs", url = "https://docs.example"),
            StoredMcpServer(name = "docs", url = "https://other.example"),
        )
        val api = storedMcpsToApi(items)
        assertEquals(listOf("docs"), api?.map { it.name })
    }

    @Test
    fun parseLines() {
        assertEquals(
            mapOf("Authorization" to "Bearer x", "X-Test" to "1"),
            parseHeaderLines("Authorization: Bearer x\n# skip\nX-Test: 1\n"),
        )
        assertEquals(mapOf("FOO" to "bar"), parseEnvLines("FOO=bar\n"))
        assertEquals(listOf("-y", "pkg"), parseArgLines("-y\npkg\n"))
    }

    @Test
    fun legacyHttpMigrates() {
        val item = migrateLegacyMcp("docs", "https://mcp.example")
        assertEquals("docs", item?.name)
        assertTrue(item?.toApi() != null)
        assertNull(migrateLegacyMcp("docs", "not-a-url"))
    }

    @Test
    fun listRoundTrip() {
        val items = listOf(StoredMcpServer(name = "docs", url = "https://mcp.example"))
        val again = decodeStoredMcps(encodeStoredMcps(items))
        assertEquals("docs", again.single().name)
        assertEquals("https://mcp.example", again.single().url)
    }
}
