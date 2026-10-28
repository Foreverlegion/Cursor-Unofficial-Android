package com.cursorandroid.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Base64
import kotlin.math.max
import kotlin.math.min

const val CUSTOM_FOLDER_ICON_PREFIX = "file:"

class CustomFolderIconStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "folder_icons").apply { mkdirs() }

    fun resolve(iconId: String): File? {
        if (!iconId.startsWith(CUSTOM_FOLDER_ICON_PREFIX)) return null
        val name = iconId.removePrefix(CUSTOM_FOLDER_ICON_PREFIX)
        if (name.isBlank() || name.contains("..")) return null
        val file = File(dir, name)
        return file.takeIf { it.isFile }
    }

    fun importFromUri(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_INPUT_BYTES) return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val square = centerCrop(bmp)
        if (square !== bmp) bmp.recycle()
        val edge = TARGET_PX
        val scaled = if (square.width == edge && square.height == edge) {
            square
        } else {
            Bitmap.createScaledBitmap(square, edge, edge, true).also {
                if (it !== square) square.recycle()
            }
        }
        val id = "ico-${System.currentTimeMillis().toString(36)}.png"
        val out = File(dir, id)
        val ok = out.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 92, it) }
        scaled.recycle()
        if (!ok) {
            out.delete()
            return null
        }
        return "$CUSTOM_FOLDER_ICON_PREFIX$id"
    }

    fun delete(iconId: String) {
        resolve(iconId)?.delete()
    }

    fun exportForBackup(membership: FolderMembership): List<FolderIconBackup> {
        val ids = membership.folders.map { it.icon }.filter { it.startsWith(CUSTOM_FOLDER_ICON_PREFIX) }.toSet()
        return ids.mapNotNull { id ->
            val file = resolve(id) ?: return@mapNotNull null
            FolderIconBackup(
                iconId = id,
                fileName = file.name,
                dataBase64 = Base64.getEncoder().encodeToString(file.readBytes()),
            )
        }
    }

    fun restoreFromBackup(items: List<FolderIconBackup>) {
        for (item in items) {
            val bytes = runCatching { Base64.getDecoder().decode(item.dataBase64) }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            val name = item.fileName.ifBlank { item.iconId.removePrefix(CUSTOM_FOLDER_ICON_PREFIX) }
            if (name.contains("..")) continue
            File(dir, name).writeBytes(bytes)
        }
    }

    private fun centerCrop(source: Bitmap): Bitmap {
        val side = min(source.width, source.height)
        if (side <= 0) return source
        val x = max(0, (source.width - side) / 2)
        val y = max(0, (source.height - side) / 2)
        return if (x == 0 && y == 0 && side == source.width && side == source.height) {
            source
        } else {
            Bitmap.createBitmap(source, x, y, side, side)
        }
    }

    companion object {
        const val TARGET_PX = 192
        private const val MAX_INPUT_BYTES = 12 * 1024 * 1024
    }
}

@Serializable
data class FolderIconBackup(
    @SerialName("iconId") val iconId: String,
    @SerialName("fileName") val fileName: String,
    @SerialName("dataBase64") val dataBase64: String,
)
