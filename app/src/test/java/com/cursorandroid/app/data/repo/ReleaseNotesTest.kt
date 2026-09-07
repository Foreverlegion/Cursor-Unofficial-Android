package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    private val remote = AppUpdate.Remote(
        versionName = "1.0.17",
        versionCode = 117,
        apkUrl = "https://github.com/Foreverlegion/Cursor-Unofficial-Android/releases/download/v1.0.17/app-release.apk",
        tag = "v1.0.17",
        notes = "## Fixes\n\n- **Faster** launch",
    )

    @Test
    fun offerNewerUnskippedApk() {
        assertTrue(ReleaseNotes.shouldOffer(remote, installedCode = 116, skippedCode = 0))
        assertFalse(ReleaseNotes.shouldOffer(remote, installedCode = 117, skippedCode = 0))
        assertFalse(ReleaseNotes.shouldOffer(remote, installedCode = 116, skippedCode = 117))
        assertTrue(ReleaseNotes.shouldOffer(remote, installedCode = 116, skippedCode = 115))
    }

    @Test
    fun skipDoesNotHideLaterVersion() {
        val next = remote.copy(versionName = "1.0.18", versionCode = 118)
        assertTrue(ReleaseNotes.shouldOffer(next, installedCode = 117, skippedCode = 117))
    }

    @Test
    fun missingApkIsNotOffered() {
        assertFalse(ReleaseNotes.shouldOffer(remote.copy(apkUrl = null), 116, 0))
        assertFalse(ReleaseNotes.shouldOffer(remote.copy(apkUrl = ""), 116, 0))
    }

    @Test
    fun fileMustMatchVersionAndHaveBody() {
        assertEquals(null, ReleaseNotes.checkFile("# 1.0.17\n\n- Notes for this release", "1.0.17"))
        assertEquals(
            "RELEASE_NOTES.md must start with '# 1.0.18'",
            ReleaseNotes.checkFile("# 1.0.17\n\n- Old notes", "1.0.18"),
        )
        assertEquals(
            "RELEASE_NOTES.md must describe version 1.0.17",
            ReleaseNotes.checkFile("# 1.0.17\n\n", "1.0.17"),
        )
        assertEquals(
            "RELEASE_NOTES.md is empty",
            ReleaseNotes.checkFile("   \n", "1.0.17"),
        )
    }

    @Test
    fun displayUsesNotesAndFallback() {
        assertEquals(
            "Fixes\n\n- Faster launch",
            ReleaseNotes.display(remote.notes, remote.versionName),
        )
        assertEquals(
            "Version 1.0.17 is available.",
            ReleaseNotes.display("   ", "1.0.17"),
        )
        assertEquals(
            "Version this release is available.",
            ReleaseNotes.display(null, "  "),
        )
    }
}
