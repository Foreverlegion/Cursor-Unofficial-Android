package com.cursorandroid.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.ImportedFontStore
import com.cursorandroid.app.ui.status.PlayColors
import com.cursorandroid.app.ui.theme.ChatDensity
import com.cursorandroid.app.ui.theme.CodeFont
import com.cursorandroid.app.ui.theme.ColorPickerDialog
import com.cursorandroid.app.ui.theme.DEFAULT_TEXT_SCALE
import com.cursorandroid.app.ui.theme.HomeDensity
import com.cursorandroid.app.ui.theme.LocalAppearance
import com.cursorandroid.app.ui.theme.MAX_TEXT_SCALE
import com.cursorandroid.app.ui.theme.MIN_TEXT_SCALE
import com.cursorandroid.app.ui.theme.ThemeColorPresets
import com.cursorandroid.app.ui.theme.UiFont
import com.cursorandroid.app.ui.theme.clampTextScale
import com.cursorandroid.app.ui.theme.formatHexColor
import com.cursorandroid.app.ui.theme.importedFontSelectionId
import com.cursorandroid.app.ui.thread.MessageText

private enum class AppearanceDialog { Font, CodeFont, ChatDensity, HomeDensity, AccentColor }

private const val PREVIEW_TEXT =
    "Here is the fix. Run the check below, then open the PR.\n```\n./gradlew assembleRelease\n```"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearancePage(container: AppContainer, onChanged: () -> Unit) {
    val store = container.store
    val context = LocalContext.current
    var themeColor by remember { mutableIntStateOf(store.themeColor) }
    var uiFontLabel by remember { mutableStateOf(readUiFontLabel(store.uiFont, container)) }
    var codeFontLabel by remember { mutableStateOf(readCodeFontLabel(store.codeFont, container)) }
    var density by remember { mutableStateOf(ChatDensity.fromId(store.chatDensity)) }
    var homeDensity by remember { mutableStateOf(HomeDensity.fromId(store.homeDensity)) }
    var imported by remember { mutableStateOf(container.importedFonts.list()) }
    var scale by remember { mutableFloatStateOf(clampTextScale(store.textScalePct).toFloat()) }
    var dialog by remember { mutableStateOf<AppearanceDialog?>(null) }
    var fontError by remember { mutableStateOf<String?>(null) }

    val importUiFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = container.importedFonts.importFromUri(context, uri, uri.lastPathSegment)) {
            is ImportedFontStore.ImportFontResult.Ok -> {
                imported = container.importedFonts.list()
                store.uiFont = importedFontSelectionId(result.id)
                uiFontLabel = readUiFontLabel(store.uiFont, container)
                fontError = null
                onChanged()
            }
            ImportedFontStore.ImportFontResult.InvalidFont -> fontError = "Could not load that font file."
            ImportedFontStore.ImportFontResult.TooLarge -> fontError = "Font file is too large."
            ImportedFontStore.ImportFontResult.ReadFailed -> fontError = "Could not read that file."
        }
    }
    val importCodeFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = container.importedFonts.importFromUri(context, uri, uri.lastPathSegment)) {
            is ImportedFontStore.ImportFontResult.Ok -> {
                imported = container.importedFonts.list()
                store.codeFont = importedFontSelectionId(result.id)
                codeFontLabel = readCodeFontLabel(store.codeFont, container)
                fontError = null
                onChanged()
            }
            ImportedFontStore.ImportFontResult.InvalidFont -> fontError = "Could not load that font file."
            ImportedFontStore.ImportFontResult.TooLarge -> fontError = "Font file is too large."
            ImportedFontStore.ImportFontResult.ReadFailed -> fontError = "Could not read that file."
        }
    }

    fun applyColor(argb: Int) {
        themeColor = argb
        store.themeColor = argb
        onChanged()
    }

    ThemePreview()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialog = AppearanceDialog.AccentColor }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "Accent color ${formatHexColor(themeColor)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(themeColor))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("Accent color", style = MaterialTheme.typography.bodyLarge)
            Text(
                formatHexColor(themeColor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()

    SettingsChoiceRow(
        title = "App font",
        summary = uiFontLabel,
        onClick = { dialog = AppearanceDialog.Font },
    )
    SettingsChoiceRow(
        title = "Code font",
        summary = codeFontLabel,
        onClick = { dialog = AppearanceDialog.CodeFont },
    )
    SettingsChoiceRow(
        title = "Chat density",
        summary = "${density.label} · ${density.summary}",
        onClick = { dialog = AppearanceDialog.ChatDensity },
    )
    SettingsChoiceRow(
        title = "Home density",
        summary = "${homeDensity.label} · ${homeDensity.summary}",
        onClick = { dialog = AppearanceDialog.HomeDensity },
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Text size", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${scale.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                enabled = scale.toInt() != DEFAULT_TEXT_SCALE,
                onClick = {
                    scale = DEFAULT_TEXT_SCALE.toFloat()
                    store.textScalePct = DEFAULT_TEXT_SCALE
                    onChanged()
                },
            ) { Text("Reset") }
        }
        Slider(
            value = scale,
            onValueChange = { scale = it },
            onValueChangeFinished = {
                val pct = clampTextScale((scale / 5f).toInt() * 5)
                scale = pct.toFloat()
                store.textScalePct = pct
                onChanged()
            },
            valueRange = MIN_TEXT_SCALE.toFloat()..MAX_TEXT_SCALE.toFloat(),
        )
    }
    HorizontalDivider()
    Text(
        "Everything stays on this phone. Inter, Roboto Mono, and JetBrains Mono are bundled under the SIL Open Font License.",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when (dialog) {
        AppearanceDialog.AccentColor -> ColorPickerDialog(
            title = "Accent color",
            initialColor = themeColor,
            presetColors = ThemeColorPresets,
            onConfirm = { picked ->
                applyColor(picked)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AppearanceDialog.Font -> AppFontDialog(
            store = store,
            container = container,
            imported = imported,
            error = fontError,
            onDismiss = { dialog = null },
            onPickBuiltin = { id ->
                store.uiFont = id
                uiFontLabel = readUiFontLabel(store.uiFont, container)
                dialog = null
                onChanged()
            },
            onPickImported = { id ->
                store.uiFont = importedFontSelectionId(id)
                uiFontLabel = readUiFontLabel(store.uiFont, container)
                dialog = null
                onChanged()
            },
            onDeleteImported = { id ->
                container.importedFonts.delete(id)
                imported = container.importedFonts.list()
                if (ImportedFontStore.importedId(store.uiFont) == id) {
                    store.uiFont = UiFont.System.id
                    uiFontLabel = readUiFontLabel(store.uiFont, container)
                    onChanged()
                }
            },
            onImport = {
                fontError = null
                importUiFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf"))
            },
        )
        AppearanceDialog.CodeFont -> AppFontDialog(
            store = store,
            container = container,
            imported = imported,
            error = fontError,
            code = true,
            onDismiss = { dialog = null },
            onPickBuiltin = { id ->
                store.codeFont = id
                codeFontLabel = readCodeFontLabel(store.codeFont, container)
                dialog = null
                onChanged()
            },
            onPickImported = { id ->
                store.codeFont = importedFontSelectionId(id)
                codeFontLabel = readCodeFontLabel(store.codeFont, container)
                dialog = null
                onChanged()
            },
            onDeleteImported = { id ->
                container.importedFonts.delete(id)
                imported = container.importedFonts.list()
                if (ImportedFontStore.importedId(store.codeFont) == id) {
                    store.codeFont = CodeFont.SystemMono.id
                    codeFontLabel = readCodeFontLabel(store.codeFont, container)
                    onChanged()
                }
            },
            onImport = {
                fontError = null
                importCodeFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf"))
            },
        )
        AppearanceDialog.ChatDensity -> ChoiceDialog(
            title = "Chat density",
            selected = density.id,
            options = ChatDensity.entries.map { it.id to "${it.label} · ${it.summary}" },
            onDismiss = { dialog = null },
            onPick = { id ->
                density = ChatDensity.fromId(id)
                store.chatDensity = density.id
                dialog = null
                onChanged()
            },
        )
        AppearanceDialog.HomeDensity -> ChoiceDialog(
            title = "Home density",
            selected = homeDensity.id,
            options = HomeDensity.entries.map { it.id to "${it.label} · ${it.summary}" },
            onDismiss = { dialog = null },
            onPick = { id ->
                homeDensity = HomeDensity.fromId(id)
                store.homeDensity = homeDensity.id
                dialog = null
                onChanged()
            },
        )
        null -> Unit
    }
}

private fun readUiFontLabel(id: String, container: AppContainer): String {
    if (ImportedFontStore.isImportedId(id)) {
        return ImportedFontStore.importedId(id)?.let { container.importedFonts.displayName(it) } ?: "Imported font"
    }
    return UiFont.fromId(id).label
}

private fun readCodeFontLabel(id: String, container: AppContainer): String {
    if (ImportedFontStore.isImportedId(id)) {
        return ImportedFontStore.importedId(id)?.let { container.importedFonts.displayName(it) } ?: "Imported font"
    }
    return CodeFont.fromId(id).label
}

@Composable
private fun AppFontDialog(
    store: com.cursorandroid.app.data.auth.ApiKeyStore,
    container: AppContainer,
    imported: List<com.cursorandroid.app.data.repo.ImportedFontEntry>,
    error: String?,
    code: Boolean = false,
    onDismiss: () -> Unit,
    onPickBuiltin: (String) -> Unit,
    onPickImported: (String) -> Unit,
    onDeleteImported: (String) -> Unit,
    onImport: () -> Unit,
) {
    val selected = if (code) store.codeFont else store.uiFont
    val builtins = if (code) {
        CodeFont.entries.map { it.id to it.label }
    } else {
        UiFont.entries.map { it.id to it.label }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (code) "Code font" else "App font") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                builtins.forEach { (id, label) ->
                    TextButton(onClick = { onPickBuiltin(id) }) {
                        Text(if (id == selected) "$label (selected)" else label)
                    }
                }
                imported.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { onPickImported(entry.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            val sel = selected == importedFontSelectionId(entry.id)
                            Text(if (sel) "${entry.name} (selected)" else entry.name)
                        }
                        TextButton(onClick = { onDeleteImported(entry.id) }) { Text("Delete") }
                    }
                }
                TextButton(onClick = onImport) { Text("Import font…") }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ThemePreview() {
    val appearance = LocalAppearance.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(appearance.rowGap.dp),
        ) {
            Text(
                "Preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                        .background(PlayColors.UserBubble)
                        .padding(horizontal = appearance.bubblePadH.dp, vertical = appearance.bubblePadV.dp),
                ) {
                    Text("Fix the build", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Agent · Claude Opus 5.5 · High",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                    .background(PlayColors.AgentBubble)
                    .padding(horizontal = appearance.bubblePadH.dp, vertical = appearance.bubblePadV.dp),
            ) {
                MessageText(PREVIEW_TEXT, Color.White)
            }
            Text(
                "Accent",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
