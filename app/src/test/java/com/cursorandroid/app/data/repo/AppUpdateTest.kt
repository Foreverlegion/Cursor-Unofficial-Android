package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateTest {
    @Test
    fun resolveRemoteUsesPublishedApkNotGradleLabel() {
        val published = AppUpdate.Remote("1.0.17", 117, "https://github.com/x/y/app.apk", "v1.0.17")
        val gradle = AppUpdate.Remote("1.0.18", 118, null, null)
        val remote = AppUpdate.resolveRemote(published, gradle)
        assertEquals("1.0.17", remote.versionName)
        assertEquals(117L, remote.versionCode)
        assertEquals(published.apkUrl, remote.apkUrl)
    }

    @Test
    fun resolveRemoteFallsBackToGradleWhenNoRelease() {
        val gradle = AppUpdate.Remote("1.0.19", 119, null, null)
        val remote = AppUpdate.resolveRemote(null, gradle)
        assertEquals("1.0.19", remote.versionName)
        assertNull(remote.apkUrl)
    }
}
