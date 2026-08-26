package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReposTest {
    @Test
    fun sanitizeKeepsSafeChars() {
        assertEquals("thermal-nexus", GithubRepos.sanitizeName("thermal-nexus"))
        assertEquals("Cursor_Unofficial.Android", GithubRepos.sanitizeName("Cursor_Unofficial.Android"))
    }

    @Test
    fun sanitizeStripsJunkAndSpaces() {
        assertEquals("my-new-repo", GithubRepos.sanitizeName("  my new repo!  "))
        assertEquals("repo", GithubRepos.sanitizeName("...repo..."))
        assertEquals("", GithubRepos.sanitizeName("!!!"))
    }

    @Test
    fun sanitizeCapsLength() {
        val long = "a".repeat(140)
        assertEquals(100, GithubRepos.sanitizeName(long).length)
    }

    @Test
    fun createBodyAlwaysSendsAutoInitAndPrivate() {
        val body = GithubRepos.createBodyJson("demo-repo", true, null)
        assertTrue(body.contains("\"name\":\"demo-repo\""))
        assertTrue(body.contains("\"private\":true"))
        assertTrue(body.contains("\"auto_init\":true"))
        assertFalse(body.contains("description"))
    }

    @Test
    fun createBodyIncludesPublicAndDescription() {
        val body = GithubRepos.createBodyJson("demo-repo", false, "hello")
        assertTrue(body.contains("\"private\":false"))
        assertTrue(body.contains("\"auto_init\":true"))
        assertTrue(body.contains("\"description\":\"hello\""))
    }

    @Test
    fun fullNameAcceptsOwnerRepo() {
        assertEquals("Foreverlegion/thermal-nexus", GithubRepos.repoFullName("Foreverlegion/thermal-nexus"))
        assertEquals("acme/app", GithubRepos.repoFullName("/acme/app/"))
        assertNull(GithubRepos.repoFullName("https://evil.example/x"))
        assertNull(GithubRepos.repoFullName("a/b/c"))
        assertNull(GithubRepos.repoFullName("../etc"))
        assertNull(GithubRepos.repoFullName(".. /.."))
    }

    @Test
    fun fullNameFromGithubHttpsUrl() {
        assertEquals(
            "Foreverlegion/demo",
            GithubRepos.repoFullNameFromUrl("https://github.com/Foreverlegion/demo.git"),
        )
        assertNull(GithubRepos.repoFullNameFromUrl("https://evil.example/Foreverlegion/demo"))
    }
}
