package com.cursorandroid.app.ui.settings

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.ui.theme.HomeDensity
import com.cursorandroid.app.ui.status.PlayColors
import com.cursorandroid.app.ui.theme.ChatDensity
import com.cursorandroid.app.ui.theme.CodeFont
import com.cursorandroid.app.ui.theme.ColorPickerDialog
import com.cursorandroid.app.ui.theme.DEFAULT_TEXT_SCALE
import com.cursorandroid.app.ui.theme.LocalAppearance
import com.cursorandroid.app.ui.theme.MAX_TEXT_SCALE
import com.cursorandroid.app.ui.theme.MIN_TEXT_SCALE
import com.cursorandroid.app.ui.theme.ThemeColorPresets
import com.cursorandroid.app.ui.theme.UiFont
import com.cursorandroid.app.ui.theme.clampTextScale
import com.cursorandroid.app.ui.theme.formatHexColor
import com.cursorandroid.app.ui.thread.MessageText

private enum class AppearanceDialog { Font, CodeFont, ChatDensity, HomeDensity, AccentColor }

private const val PREVIEW_TEXT =
    "Here is the fix. Run the check below, then open the PR.\n```\n./gradlew assembleRelease\n```"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearancePage(container: AppContainer, onChanged: () -> Unit) {
    val store = container.store
    var themeColor by remember { mutableIntStateOf(store.themeColor) }
    var uiFont by remember { mutableStateOf(UiFont.fromId(store.uiFont)) }
    var codeFont by remember { mutableStateOf(CodeFont.fromId(store.codeFont)) }
    var density by remember { mutableStateOf(ChatDensity.fromId(store.chatDensity)) }
    var homeDensity by remember { mutableStateOf(HomeDensity.fromId(store.homeDensity)) }
    var imported by remember { mutableStateOf(container.importedFonts.list()) }
    var fontError by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(clampTextScale(store.textScalePct).toFloat()) }
    var dialog by remember { mutableStateOf<AppearanceDialog?>(null) }

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
        summary = fontSummary(store.uiFont, imported),
        onClick = { dialog = AppearanceDialog.Font },
    )
    SettingsChoiceRow(
        title = "Code font",
        summary = fontSummary(store.codeFont, imported, bundled = CodeFont.entries.map { it.id to it.label }),
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
        AppearanceDialog.Font -> AppFontPickerDialog(
            title = "App font",
            selected = store.uiFont,
            imported = imported,
            error = fontError,
            onDismiss = {
                fontError = null
                dialog = null
            },
            onPickBundled = { id ->
                uiFont = UiFont.fromId(id)
                store.uiFont = uiFont.id
                fontError = null
                dialog = null
                onChanged()
            },
            onPickImported = { id ->
                store.uiFont = container.importedFonts.ref(id)
                uiFont = UiFont.System
                fontError = null
                dialog = null
                onChanged()
            },
            onImport = { uri, name ->
                container.importedFonts.importFromUri(uri, name).fold(
                    onSuccess = {
                        imported = container.importedFonts.list()
                        store.uiFont = container.importedFonts.ref(it.id)
                        fontError = null
                        dialog = null
                        onChanged()
                    },
                    onFailure = { fontError = it.message ?: "Could not import font" },
                )
            },
            onDeleteImported = { id ->
                if (container.importedFonts.isImportRef(store.uiFont) &&
                    container.importedFonts.importId(store.uiFont) == id
                ) {
                    store.uiFont = UiFont.System.id
                }
                container.importedFonts.delete(id)
                imported = container.importedFonts.list()
                onChanged()
            },
        )
        AppearanceDialog.CodeFont -> AppFontPickerDialog(
            title = "Code font",
            selected = store.codeFont,
            imported = imported,
            error = fontError,
            bundled = CodeFont.entries.map { it.id to it.label },
            onDismiss = {
                fontError = null
                dialog = null
            },
            onPickBundled = { id ->
                codeFont = CodeFont.fromId(id)
                store.codeFont = codeFont.id
                fontError = null
                dialog = null
                onChanged()
            },
            onPickImported = { id ->
                store.codeFont = container.importedFonts.ref(id)
                codeFont = CodeFont.SystemMono
                fontError = null
                dialog = null
                onChanged()
            },
            onImport = { uri, name ->
                container.importedFonts.importFromUri(uri, name).fold(
                    onSuccess = {
                        imported = container.importedFonts.list()
                        store.codeFont = container.importedFonts.ref(it.id)
                        fontError = null
                        dialog = null
                        onChanged()
                    },
                    onFailure = { fontError = it.message ?: "Could not import font" },
                )
            },
            onDeleteImported = { id ->
                if (container.importedFonts.isImportRef(store.codeFont) &&
                    container.importedFonts.importId(store.codeFont) == id
                ) {
                    store.codeFont = CodeFont.SystemMono.id
                }
                container.importedFonts.delete(id)
                imported = container.importedFonts.list()
                onChanged()
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

private fun fontSummary(
    selected: String,
    imported: List<com.cursorandroid.app.data.repo.ImportedFontRecord>,
    bundled: List<Pair<String, String>> = UiFont.entries.map { it.id to it.label },
): String {
    val importId = selected.removePrefix(com.cursorandroid.app.data.repo.ImportedFontStore.PREFIX)
    if (selected.startsWith(com.cursorandroid.app.data.repo.ImportedFontStore.PREFIX)) {
        return imported.firstOrNull { it.id == importId }?.label ?: "Imported font"
    }
    return bundled.firstOrNull { it.first == selected }?.second ?: selected
}

@Composable
private fun AppFontPickerDialog(
    title: String,
    selected: String,
    imported: List<com.cursorandroid.app.data.repo.ImportedFontRecord>,
    error: String?,
    bundled: List<Pair<String, String>> = UiFont.entries.map { it.id to it.label },
    onDismiss: () -> Unit,
    onPickBundled: (String) -> Unit,
    onPickImported: (String) -> Unit,
    onImport: (android.net.Uri, String?) -> Unit,
    onDeleteImported: (String) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri, uri.lastPathSegment)
    }
    ChoiceDialog(
        title = title,
        selected = if (selected.startsWith(com.cursorandroid.app.data.repo.ImportedFontStore.PREFIX)) {
            selected
        } else {
            selected
        },
        options = bundled +
            imported.map { com.cursorandroid.app.data.repo.ImportedFontStore.PREFIX + it.id to it.label },
        onDismiss = onDismiss,
        onPick = { id ->
            if (id.startsWith(com.cursorandroid.app.data.repo.ImportedFontStore.PREFIX)) {
                onPickImported(id.removePrefix(com.cursorandroid.app.data.repo.ImportedFontStore.PREFIX))
            } else {
                onPickBundled(id)
            }
        },
        footer = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!error.isNullOrBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                TextButton(
                    onClick = {
                        picker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf"))
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("Import font…") }
                imported.forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(record.label, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDeleteImported(record.id) }) { Text("Delete") }
                    }
                }
            }
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
