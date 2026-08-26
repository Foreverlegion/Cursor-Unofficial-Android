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
                        imageCount += 1
                        val cache = writeCache(context, name, bytes)
                        AttachItem(
                            id = uri.toString(),
                            name = name,
                            mime = mime,
                            image = PromptImage(
                                data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                mimeType = mime,
                            ),
                            thumbPath = writeThumb(context, name, bytes),
                            cachePath = cache,
                        )
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

    private fun isImage(mime: String): Boolean {
        return mime in setOf("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp")
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
            AttachItem(
                id = path,
                name = name,
                mime = mime,
                image = PromptImage(
                    data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    mimeType = mime,
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

    private fun writeCache(context: Context, name: String, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}_${name.filter { it.isLetterOrDigit() || it == '.' }}")
        file.writeBytes(bytes)
        return file.absolutePath
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
}
