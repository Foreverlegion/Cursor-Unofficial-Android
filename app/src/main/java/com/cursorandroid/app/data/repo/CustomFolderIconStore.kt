package com.cursorandroid.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Base64
import kotlin.math.max
import kotlin.math.min

const val CUSTOM_FOLDER_ICON_PREFIX = "file:"

class CustomFolderIconStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "folder_icons").apply { mkdirs() }

    fun resolve(iconId: String): File? {
        if (!iconId.startsWith(CUSTOM_FOLDER_ICON_PREFIX)) return null
        val name = iconId.removePrefix(CUSTOM_FOLDER_ICON_PREFIX)
        if (name.isBlank() || name.contains("..") || name.contains('/')) return null
        val file = File(dir, name)
        return file.takeIf { it.isFile }
    }

    fun iconIdForFileName(fileName: String): String = "$CUSTOM_FOLDER_ICON_PREFIX$fileName"

    fun fileNameForFolder(folderId: String): String? {
        val safe = sanitizeFolderId(folderId) ?: return null
        return "$safe.webp"
    }

    fun importForFolder(context: Context, uri: Uri, folderId: String): FolderIconImportResult {
        val fileName = fileNameForFolder(folderId) ?: return FolderIconImportResult.Failure("Couldn't save that icon.")
        val outFile = File(dir, fileName)
        val sizeBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
            fd.length.takeIf { it >= 0 }
        }
        val bounds = readBounds(context, uri)
            ?: return FolderIconImportResult.Failure("Couldn't read that image.")
        FolderIconLimits.inputRejectMessage(sizeBytes, bounds.width, bounds.height)?.let {
            return FolderIconImportResult.Failure(it)
        }
        return try {
            val bytes = decodeAndCompress(context, uri, bounds)
                ?: return FolderIconImportResult.Failure("Couldn't use that image. Try another photo.")
            outFile.writeBytes(bytes)
            pruneLegacyFilesForFolder(folderId, keepFileName = fileName)
            FolderIconImportResult.Success(iconIdForFileName(fileName))
        } catch (_: IOException) {
            FolderIconImportResult.Failure("Couldn't read that image.")
        } catch (_: OutOfMemoryError) {
            FolderIconImportResult.Failure("That photo is too large to process on this device.")
        } catch (_: SecurityException) {
            FolderIconImportResult.Failure("Couldn't access that photo.")
        }
    }

    fun delete(iconId: String) {
        resolve(iconId)?.delete()
    }

    fun deleteForFolder(folderId: String) {
        fileNameForFolder(folderId)?.let { name -> File(dir, name).delete() }
        pruneLegacyFilesForFolder(folderId, keepFileName = null)
    }

    /** Deletes files in [folder_icons] that are not referenced by any folder icon id. */
    fun pruneOrphans(referencedIconIds: Set<String>) {
        val keepNames = referencedIconIds.mapNotNull { resolve(it)?.name }.toSet()
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keepNames) {
                file.delete()
            }
        }
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
            if (name.contains("..") || name.contains('/')) continue
            File(dir, name).writeBytes(bytes)
        }
    }

    private fun decodeAndCompress(context: Context, uri: Uri, bounds: ImageBounds): ByteArray? {
        val decoded = decodeDownsampled(context, uri, bounds) ?: return null
        val square = centerCrop(decoded)
        if (square !== decoded) decoded.recycle()
        val scaled = scaleToEdge(square, FolderIconLimits.TARGET_EDGE_PX)
        if (scaled !== square) square.recycle()
        val bytes = compressUnderBudget(scaled)
        scaled.recycle()
        return bytes
    }

    private data class ImageBounds(val width: Int, val height: Int)

    private fun readBounds(context: Context, uri: Uri): ImageBounds? = readBoundsLegacy(context, uri)

    private fun readBoundsLegacy(context: Context, uri: Uri): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return ImageBounds(options.outWidth, options.outHeight)
    }

    private fun decodeDownsampled(context: Context, uri: Uri, bounds: ImageBounds): Bitmap? {
        val sample = FolderIconLimits.computeInSampleSize(bounds.width, bounds.height)
        if (Build.VERSION.SDK_INT >= 28) {
            return try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    if (sample > 1) {
                        val w = (bounds.width / sample).coerceAtLeast(1)
                        val h = (bounds.height / sample).coerceAtLeast(1)
                        decoder.setTargetSize(w, h)
                    }
                }
            } catch (_: Exception) {
                decodeDownsampledLegacy(context, uri, sample)
            }
        }
        return decodeDownsampledLegacy(context, uri, sample)?.let { applyExifRotation(context, uri, it) }
    }

    private fun decodeDownsampledLegacy(context: Context, uri: Uri, sample: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return applyExifRotation(context, uri, raw)
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleToEdge(source: Bitmap, edge: Int): Bitmap {
        return if (source.width == edge && source.height == edge) {
            source
        } else {
            Bitmap.createScaledBitmap(source, edge, edge, true)
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

    private fun compressUnderBudget(bitmap: Bitmap): ByteArray? {
        var quality = FolderIconLimits.WEBP_QUALITY
        var smallest: ByteArray? = null
        repeat(6) {
            val bytes = compressWebp(bitmap, quality)
            if (bytes != null) {
                smallest = bytes
                if (bytes.size <= FolderIconLimits.TARGET_MAX_OUTPUT_BYTES) return bytes
            }
            quality = (quality - 12).coerceAtLeast(40)
        }
        if (smallest != null && smallest!!.size <= FolderIconLimits.TARGET_MAX_OUTPUT_BYTES * 2) {
            return smallest
        }
        val png = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) return smallest
        val pngBytes = png.toByteArray()
        return when {
            pngBytes.size <= FolderIconLimits.TARGET_MAX_OUTPUT_BYTES * 2 -> pngBytes
            smallest != null -> smallest
            else -> null
        }
    }

    private fun compressWebp(bitmap: Bitmap, quality: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val ok = if (Build.VERSION.SDK_INT >= 30) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
        } else {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
        }
        return if (ok && out.size() > 0) out.toByteArray() else null
    }

    private fun sanitizeFolderId(folderId: String): String? {
        val trimmed = folderId.trim()
        if (trimmed.isEmpty() || trimmed.contains("..") || trimmed.contains('/')) return null
        if (!trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
        return trimmed
    }

    private fun pruneLegacyFilesForFolder(folderId: String, keepFileName: String?) {
        val prefix = sanitizeFolderId(folderId) ?: return
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (keepFileName != null && file.name == keepFileName) return@forEach
            if (file.name.startsWith("ico-") || file.name.startsWith("$prefix.")) {
                file.delete()
            }
        }
    }
}

@Serializable
data class FolderIconBackup(
    @SerialName("iconId") val iconId: String,
    @SerialName("fileName") val fileName: String,
    @SerialName("dataBase64") val dataBase64: String,
)
