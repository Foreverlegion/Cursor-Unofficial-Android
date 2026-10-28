package com.cursorandroid.app.data.repo

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

@Serializable
data class ImportedFontEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("file") val file: String,
)

const val IMPORTED_FONT_PREFIX = "import:"

class ImportedFontStore(
    context: Context,
    private val ui: UiPrefsStore,
) {
    private val dir = File(context.applicationContext.filesDir, "imported_fonts").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<ImportedFontEntry> {
        val raw = ui[UiKeys.importedFonts] ?: return emptyList()
        return runCatching { json.decodeFromString<List<ImportedFontEntry>>(raw) }.getOrDefault(emptyList())
    }

    fun family(id: String): FontFamily? {
        val entry = list().firstOrNull { it.id == id } ?: return null
        val file = File(dir, entry.file)
        if (!file.isFile) return null
        return runCatching {
            Typeface.createFromFile(file)
            FontFamily(Font(file))
        }.getOrNull()
    }

    fun displayName(id: String): String? = list().firstOrNull { it.id == id }?.name

    fun importFromUri(context: Context, uri: Uri, displayName: String?): ImportFontResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ImportFontResult.ReadFailed
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return ImportFontResult.TooLarge
        val ext = when {
            displayName.orEmpty().endsWith(".otf", ignoreCase = true) -> "otf"
            displayName.orEmpty().endsWith(".ttf", ignoreCase = true) -> "ttf"
            else -> "ttf"
        }
        val id = "fnt-${System.currentTimeMillis().toString(36)}"
        val fileName = "$id.$ext"
        val out = File(dir, fileName)
        out.writeBytes(bytes)
        val valid = runCatching { Typeface.createFromFile(out) }.isSuccess
        if (!valid) {
            out.delete()
            return ImportFontResult.InvalidFont
        }
        val label = displayName?.substringBeforeLast('.')?.trim()?.take(NAME_MAX)
            ?.takeIf { it.isNotEmpty() }
            ?: "Imported font"
        val entry = ImportedFontEntry(id = id, name = label, file = fileName)
        val next = list() + entry
        persist(next)
        return ImportFontResult.Ok(id)
    }

    fun delete(id: String) {
        val current = list()
        val hit = current.firstOrNull { it.id == id } ?: return
        File(dir, hit.file).delete()
        persist(current.filterNot { it.id == id })
    }

    fun exportForBackup(): List<ImportedFontBackup> =
        list().mapNotNull { entry ->
            val file = File(dir, entry.file)
            if (!file.isFile) return@mapNotNull null
            ImportedFontBackup(
                id = entry.id,
                name = entry.name,
                file = entry.file,
                dataBase64 = Base64.getEncoder().encodeToString(file.readBytes()),
            )
        }

    fun restoreFromBackup(items: List<ImportedFontBackup>) {
        if (items.isEmpty()) return
        val merged = list().associateBy { it.id }.toMutableMap()
        for (item in items) {
            val bytes = runCatching { Base64.getDecoder().decode(item.dataBase64) }.getOrNull() ?: continue
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) continue
            val out = File(dir, item.file)
            out.writeBytes(bytes)
            if (runCatching { Typeface.createFromFile(out) }.isFailure) {
                out.delete()
                continue
            }
            merged[item.id] = ImportedFontEntry(item.id, item.name, item.file)
        }
        persist(merged.values.toList())
    }

    private fun persist(entries: List<ImportedFontEntry>) {
        ui.put(UiKeys.importedFonts, json.encodeToString(entries))
        ui.flush()
    }

    sealed class ImportFontResult {
        data class Ok(val id: String) : ImportFontResult()
        data object InvalidFont : ImportFontResult()
        data object TooLarge : ImportFontResult()
        data object ReadFailed : ImportFontResult()
    }

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        const val NAME_MAX = 48

        fun isImportedId(value: String?): Boolean =
            value?.startsWith(IMPORTED_FONT_PREFIX) == true

        fun importedId(value: String): String? =
            value.removePrefix(IMPORTED_FONT_PREFIX).takeIf { it.isNotEmpty() }
    }
}

@Serializable
data class ImportedFontBackup(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("file") val file: String,
    @SerialName("dataBase64") val dataBase64: String,
)
