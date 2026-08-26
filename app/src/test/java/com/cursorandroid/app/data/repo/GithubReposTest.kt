package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
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
}
