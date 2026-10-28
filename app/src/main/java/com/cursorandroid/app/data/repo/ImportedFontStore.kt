package com.cursorandroid.app.data.repo

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Serializable
data class ImportedFontRecord(
    val id: String,
    val label: String,
    val fileName: String,
)

class ImportedFontStore(
    private val ui: UiPrefsStore,
    context: Context,
) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "imported-fonts").also { it.mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun list(): List<ImportedFontRecord> = load()

    fun ref(id: String): String = "$PREFIX$id"

    fun isImportRef(value: String): Boolean = value.startsWith(PREFIX)

    fun importId(value: String): String? =
        value.takeIf { isImportRef(it) }?.removePrefix(PREFIX)?.takeIf { it.isNotBlank() }

    fun replaceAll(records: List<ImportedFontRecord>) {
        ui.put(UiKeys.importedFonts, json.encodeToString(records))
        ui.flush()
    }

    /**
     * Copies the font into app-private storage and validates it loads as a [Typeface].
     */
    fun importFromUri(uri: Uri, suggestedName: String?): Result<ImportedFontRecord> {
        val name = suggestedName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Imported font"
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val ext = when {
            suggestedName?.endsWith(".otf", ignoreCase = true) == true -> "otf"
            suggestedName?.endsWith(".ttf", ignoreCase = true) == true -> "ttf"
            else -> "ttf"
        }
        val fileName = "$id.$ext"
        val dest = File(dir, fileName)
        val input = app.contentResolver.openInputStream(uri) ?: return Result.failure(IllegalStateException("Could not read font"))
        input.use { stream ->
            FileOutputStream(dest).use { out -> stream.copyTo(out) }
        }
        val typeface = runCatching { Typeface.createFromFile(dest) }.getOrNull()
        if (typeface == null) {
            dest.delete()
            return Result.failure(IllegalStateException("Not a valid font file"))
        }
        val record = ImportedFontRecord(id = id, label = name.take(LABEL_MAX), fileName = fileName)
        val next = load().filterNot { it.id == id } + record
        replaceAll(next)
        return Result.success(record)
    }

    fun delete(id: String) {
        val current = load()
        val hit = current.firstOrNull { it.id == id } ?: return
        File(dir, hit.fileName).delete()
        replaceAll(current.filterNot { it.id == id })
    }

    fun typefaceForRef(fontRef: String): Typeface? {
        val id = importId(fontRef) ?: return null
        val record = load().firstOrNull { it.id == id } ?: return null
        val file = File(dir, record.fileName)
        if (!file.isFile) return null
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }

    fun composeFamily(fontRef: String): FontFamily? {
        val id = importId(fontRef) ?: return null
        val record = load().firstOrNull { it.id == id } ?: return null
        val file = File(dir, record.fileName)
        if (!file.isFile) return null
        return FontFamily(Font(file, weight = FontWeight.Normal))
    }

    fun labelForRef(fontRef: String): String? {
        val id = importId(fontRef) ?: return null
        return load().firstOrNull { it.id == id }?.label
    }

    fun restoreFiles(records: List<ImportedFontRecord>, encoded: Map<String, String>) {
        replaceAll(records)
        encoded.forEach { (id, b64) ->
            val record = records.firstOrNull { it.id == id } ?: return@forEach
            val bytes = runCatching { android.util.Base64.decode(b64, android.util.Base64.NO_WRAP) }.getOrNull()
                ?: return@forEach
            FileOutputStream(File(dir, record.fileName)).use { it.write(bytes) }
        }
    }

    fun exportFileData(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        load().forEach { record ->
            val file = File(dir, record.fileName)
            if (!file.isFile) return@forEach
            out[record.id] = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
        }
        return out
    }

    private fun load(): List<ImportedFontRecord> {
        val raw = ui[UiKeys.importedFonts] ?: return emptyList()
        return runCatching { json.decodeFromString<List<ImportedFontRecord>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        const val PREFIX = "import:"
        private const val LABEL_MAX = 48
    }
}
