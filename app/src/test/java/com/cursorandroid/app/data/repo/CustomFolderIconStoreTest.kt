package com.cursorandroid.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomFolderIconStoreTest {
    private lateinit var context: Context
    private lateinit var store: CustomFolderIconStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = CustomFolderIconStore(context)
    }

    @Test
    fun importScalesLargeJpegToSquareWebp() {
        val source = File(context.cacheDir, "folder-icon-src.jpg")
        Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888).use { bmp ->
            FileOutputStream(source).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        }
        val uri = Uri.fromFile(source)
        val result = store.importForFolder(context, uri, "fld-robo-test")
        assertTrue(result is FolderIconImportResult.Success)
        val iconId = (result as FolderIconImportResult.Success).iconId
        assertEquals("file:fld-robo-test.webp", iconId)
        val file = store.resolve(iconId)!!
        assertTrue(file.length() in 1..(FolderIconLimits.TARGET_MAX_OUTPUT_BYTES * 2))
        val decoded = BitmapFactory.decodeFile(file.absolutePath)!!
        assertEquals(FolderIconLimits.TARGET_EDGE_PX, decoded.width)
        assertEquals(FolderIconLimits.TARGET_EDGE_PX, decoded.height)
        decoded.recycle()
    }

    @Test
    fun pruneOrphansDropsUnreferencedFiles() {
        val orphan = File(context.filesDir, "folder_icons/orphan.webp")
        orphan.parentFile?.mkdirs()
        orphan.writeBytes(byteArrayOf(1, 2, 3))
        store.pruneOrphans(emptySet())
        assertTrue(!orphan.exists())
    }

    private inline fun Bitmap.use(block: (Bitmap) -> Unit) {
        try {
            block(this)
        } finally {
            recycle()
        }
    }
}
