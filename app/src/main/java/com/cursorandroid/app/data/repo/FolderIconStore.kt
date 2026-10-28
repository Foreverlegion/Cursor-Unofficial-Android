package com.cursorandroid.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class FolderIconStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "folder-icons").also { it.mkdirs() }

    fun iconRef(folderId: String): String = "$PREFIX$folderId"

    fun isCustom(icon: String): Boolean = icon.startsWith(PREFIX)

    fun fileFor(icon: String): File? {
        if (!isCustom(icon)) return null
        val id = icon.removePrefix(PREFIX)
        if (id.isBlank()) return null
        return File(dir, "$id.png")
    }

    fun loadBitmap(icon: String): Bitmap? {
        val file = fileFor(icon) ?: return null
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun delete(icon: String) {
        fileFor(icon)?.delete()
    }

    /**
     * Center-crops to a square, downscales to [TARGET_PX], writes WebP under app-private storage.
     * Returns the icon ref stored on [LocalFolder.icon].
     */
    fun importForFolder(folderId: String, uri: Uri): String? {
        if (folderId.isBlank()) return null
        val stream = app.contentResolver.openInputStream(uri) ?: return null
        stream.use {
            val decoded = BitmapFactory.decodeStream(it) ?: return null
            val square = centerCropSquare(decoded)
            if (square !== decoded) decoded.recycle()
            val scaled = if (max(square.width, square.height) > TARGET_PX) {
                val ratio = TARGET_PX.toFloat() / max(square.width, square.height)
                Bitmap.createScaledBitmap(
                    square,
                    max(1, (square.width * ratio).toInt()),
                    max(1, (square.height * ratio).toInt()),
                    true,
                ).also { if (it !== square) square.recycle() }
            } else {
                square
            }
            val out = File(dir, "$folderId.png")
            FileOutputStream(out).use { fos ->
                scaled.compress(Bitmap.CompressFormat.PNG, 92, fos)
            }
            scaled.recycle()
        }
        return iconRef(folderId)
    }

    fun exportBase64(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val id = file.nameWithoutExtension
            val bytes = file.readBytes()
            if (bytes.isNotEmpty()) out[id] = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
        return out
    }

    fun restoreFromBackup(encoded: Map<String, String>) {
        encoded.forEach { (id, b64) ->
            val bytes = runCatching { android.util.Base64.decode(b64, android.util.Base64.NO_WRAP) }.getOrNull()
                ?: return@forEach
            if (bytes.isEmpty()) return@forEach
            FileOutputStream(File(dir, "$id.png")).use { it.write(bytes) }
        }
    }

    companion object {
        const val PREFIX = "custom:"
        private const val TARGET_PX = 192

        private fun centerCropSquare(source: Bitmap): Bitmap {
            val side = min(source.width, source.height)
            if (side <= 0) return source
            if (source.width == side && source.height == side) return source
            val x = (source.width - side) / 2
            val y = (source.height - side) / 2
            return Bitmap.createBitmap(source, x, y, side, side)
        }
    }
}
