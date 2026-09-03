package com.cursorandroid.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.PromptImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class AttachItem(
    val id: String,
    val name: String,
    val mime: String,
    val image: PromptImage? = null,
    val excerpt: String? = null,
    val error: String? = null,
    val thumbPath: String? = null,
    val cachePath: String? = null,
) {
    val isImage: Boolean get() = image != null
    val ok: Boolean get() = error == null
}

object Attachments {
    const val MAX_IMAGES = 5
    const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L
    const val MAX_TEXT_BYTES = 200L * 1024L
    const val MAX_EDGE = 2048
    private const val JPEG_QUALITY = 85

    fun normalizeMime(mime: String): String {
        return when (mime.lowercase()) {
            "image/jpg", "image/pjpeg" -> "image/jpeg"
            else -> mime.lowercase()
        }
    }

    fun isOfficialImageMime(mime: String): Boolean {
        return normalizeMime(mime) in OFFICIAL_IMAGE
    }

    fun read(context: Context, uris: List<Uri>, alreadyImages: Int = 0): List<AttachItem> {
        var imageCount = alreadyImages
        return uris.mapNotNull { uri ->
            val name = displayName(context, uri) ?: uri.lastPathSegment ?: "file"
            val mime = context.contentResolver.getType(uri)
                ?: guessMime(name)
                ?: "application/octet-stream"
            if (!isImage(mime) && !isText(mime, name)) {
                return@mapNotNull AttachItem(
                    id = uri.toString(),
                    name = name,
                    mime = mime,
                    error = "Cloud Agents accept images or text files. $name is $mime.",
                )
            }
            val limit = if (isImage(mime)) MAX_IMAGE_BYTES else MAX_TEXT_BYTES
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { SafeLinks.readBounded(it, limit) }
            }.getOrNull()
            if (bytes == null) {
                val over = if (isImage(mime)) "15 MB" else "200 KB"
                return@mapNotNull AttachItem(uri.toString(), name, mime, error = "Could not read $name or it is over $over")
            }
            when {
                isImage(mime) -> {
                    if (imageCount >= MAX_IMAGES) {
                        AttachItem(uri.toString(), name, mime, error = "Max $MAX_IMAGES images")
                    } else if (bytes.size > MAX_IMAGE_BYTES) {
                        AttachItem(uri.toString(), name, mime, error = "$name is over 15 MB")
                    } else {
                        val raster = rasterize(bytes, mime)
                        if (raster == null) {
                            AttachItem(uri.toString(), name, mime, error = "Could not read $name as an image")
                        } else {
                            imageCount += 1
                            val (outBytes, outMime) = raster
                            val cache = writeCache(context, name, outBytes, extFor(outMime))
                            AttachItem(
                                id = uri.toString(),
                                name = name,
                                mime = outMime,
                                image = PromptImage(
                                    data = Base64.encodeToString(outBytes, Base64.NO_WRAP),
                                    mimeType = outMime,
                                ),
                                thumbPath = writeThumb(context, name, outBytes),
                                cachePath = cache,
                            )
                        }
                    }
                }
                isText(mime, name) -> {
                    if (bytes.size > MAX_TEXT_BYTES) {
                        AttachItem(uri.toString(), name, mime, error = "$name is over 200 KB")
                    } else {
                        AttachItem(
                            id = uri.toString(),
                            name = name,
                            mime = mime,
                            excerpt = bytes.toString(Charsets.UTF_8),
                            cachePath = writeCache(context, name, bytes),
                        )
                    }
                }
                else -> AttachItem(
                    id = uri.toString(),
                    name = name,
                    mime = mime,
                    error = "Cloud Agents accept images or text files. $name is $mime.",
                )
            }
        }
    }

    fun prompt(text: String, items: List<AttachItem>): Prompt {
        val files = items.filter { it.ok && it.excerpt != null }
        val body = buildString {
            val trimmed = text.trim()
            if (trimmed.isNotEmpty()) append(trimmed)
            files.forEach { item ->
                if (isNotEmpty()) append("\n\n")
                append("Attached file: ")
                append(item.name)
                append("\n```\n")
                append(item.excerpt)
                append("\n```")
            }
        }.ifBlank { "See attached." }
        val images = items.mapNotNull { it.image }.take(MAX_IMAGES)
        return Prompt(text = body, images = images.takeIf { it.isNotEmpty() })
    }

    fun label(text: String, items: List<AttachItem>): String {
        val names = items.filter { it.ok }.joinToString(", ") { it.name }
        val trimmed = text.trim()
        return when {
            trimmed.isNotEmpty() && names.isNotEmpty() -> "$trimmed\n[$names]"
            trimmed.isNotEmpty() -> trimmed
            names.isNotEmpty() -> names
            else -> ""
        }
    }

    fun forget(items: List<AttachItem>) {
        items.forEach { item ->
            item.cachePath?.let { path -> File(path).delete() }
        }
    }

    private fun isImage(mime: String): Boolean {
        val n = normalizeMime(mime)
        return n in OFFICIAL_IMAGE || n in HEIF
    }

    private fun isText(mime: String, name: String): Boolean {
        if (mime.startsWith("text/")) return true
        if (mime in setOf(
                "application/json",
                "application/xml",
                "application/javascript",
                "application/typescript",
                "application/x-yaml",
                "application/yaml",
            )
        ) {
            return true
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "txt", "md", "json", "xml", "yml", "yaml", "csv", "log",
            "kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "rb",
            "go", "rs", "c", "h", "cpp", "hpp", "cs", "swift",
            "html", "css", "scss", "gradle", "toml", "ini", "env",
            "sh", "bat", "ps1", "sql", "proto",
        )
    }

    fun fromCache(path: String, name: String, mime: String): AttachItem {
        val file = File(path)
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null) {
            return AttachItem(path, name, mime, error = "Draft file missing")
        }
        return if (isImage(mime)) {
            val raster = rasterize(bytes, mime) ?: return AttachItem(path, name, mime, error = "Could not read $name as an image")
            val (outBytes, outMime) = raster
            AttachItem(
                id = path,
                name = name,
                mime = outMime,
                image = PromptImage(
                    data = Base64.encodeToString(outBytes, Base64.NO_WRAP),
                    mimeType = outMime,
                ),
                thumbPath = writeThumbFromFile(file),
                cachePath = path,
            )
        } else {
            AttachItem(
                id = path,
                name = name,
                mime = mime,
                excerpt = bytes.toString(Charsets.UTF_8),
                cachePath = path,
            )
        }
    }

    private fun writeCache(context: Context, name: String, bytes: ByteArray, ext: String? = null): String {
        val dir = File(context.filesDir, "attaches").apply { mkdirs() }
        val safe = name.filter { it.isLetterOrDigit() || it == '.' }.ifBlank { "file" }
        val fileName = if (ext != null && !safe.endsWith(".$ext", ignoreCase = true)) {
            "${System.currentTimeMillis()}_$safe.$ext"
        } else {
            "${System.currentTimeMillis()}_$safe"
        }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun rasterize(bytes: ByteArray, mime: String): Pair<ByteArray, String>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null
        var sample = 1
        val longest = maxOf(srcW, srcH)
        while (longest / sample > MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) return null
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(bmp.width, bmp.height))
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (w == bmp.width && h == bmp.height) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
        val out = ByteArrayOutputStream()
        val png = normalizeMime(mime) == "image/png" && scaled.hasAlpha()
        val ok = if (png) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        } else {
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        if (!ok) return null
        val outBytes = out.toByteArray()
        if (outBytes.isEmpty() || outBytes.size > MAX_IMAGE_BYTES) return null
        return outBytes to if (png) "image/png" else "image/jpeg"
    }

    private fun extFor(mime: String): String {
        return if (normalizeMime(mime) == "image/png") "png" else "jpg"
    }

    private fun writeThumb(context: Context, name: String, bytes: ByteArray): String? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) return null
        val w = 256
        val h = (bmp.height * w / bmp.width.toFloat()).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}_${name.hashCode()}.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return file.absolutePath
    }

    private fun writeThumbFromFile(file: File): String? {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) return null
        val w = 256
        val h = (bmp.height * w / bmp.width.toFloat()).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val out = File(file.parentFile, "${file.name}.thumb.jpg")
        FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return out.absolutePath
    }

    private fun displayName(context: Context, uri: Uri): String? {
        val cursor = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        }.getOrNull() ?: return null
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun guessMime(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private val OFFICIAL_IMAGE = setOf("image/png", "image/jpeg", "image/gif", "image/webp")
    private val HEIF = setOf("image/heic", "image/heif", "image/heic-sequence")
}
