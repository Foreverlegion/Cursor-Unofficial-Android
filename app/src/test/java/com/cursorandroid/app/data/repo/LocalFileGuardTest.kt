package com.cursorandroid.app.data.repo

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalFileGuardTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun shareAcceptsOnlyForeignContentUris() {
        val own = "com.cursorandroid.app"
        assertTrue(Attachments.readable(Uri.parse("content://com.android.providers.media.documents/document/1"), own))
        assertFalse(Attachments.readable(Uri.parse("file:///data/data/$own/shared_prefs/cursor_secure.xml"), own))
        assertFalse(Attachments.readable(Uri.parse("content://$own.androidx-startup/x"), own))
        assertFalse(Attachments.readable(Uri.parse("content://$own/x"), own))
        assertFalse(Attachments.readable(Uri.parse("https://example.com/a.png"), own))
    }

    @Test
    fun cachePathsStayInsideRoots() {
        val root = File(tmp.root, "attaches").apply { mkdirs() }
        val roots = listOf(root.canonicalPath)
        assertTrue(Attachments.within(File(root, "1_a.png").path, roots))
        assertFalse(Attachments.within(File(root, "../shared_prefs/cursor_secure.xml").path, roots))
        assertFalse(Attachments.within(root.path, roots))
        assertFalse(Attachments.within(tmp.root.path + "/attaches-evil/x", roots))
        assertFalse(Attachments.within("", roots))
        assertFalse(Attachments.within(File(root, "x").path, emptyList()))
    }

    @Test
    fun artifactNamesCannotLeaveDownloads() {
        assertEquals("artifact", ArtifactSaver.fileName(".."))
        assertEquals("artifact", ArtifactSaver.fileName("a/../"))
        assertEquals("passwd", ArtifactSaver.fileName("../../etc/passwd"))
        assertEquals("env", ArtifactSaver.fileName(".env"))
        assertEquals("report.txt", ArtifactSaver.fileName("out\\report.txt"))
        assertEquals("ab.log", ArtifactSaver.fileName("a\u0000b.log"))
        assertEquals("artifact", ArtifactSaver.fileName(""))
        assertEquals(120, ArtifactSaver.fileName("x".repeat(300)).length)
    }
}
