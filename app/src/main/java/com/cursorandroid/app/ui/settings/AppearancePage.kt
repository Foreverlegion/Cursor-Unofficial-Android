package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.ui.status.PlayColors
import com.cursorandroid.app.ui.theme.ChatDensity
import com.cursorandroid.app.ui.theme.CodeFont
import com.cursorandroid.app.ui.theme.DEFAULT_TEXT_SCALE
import com.cursorandroid.app.ui.theme.LocalAppearance
import com.cursorandroid.app.ui.theme.MAX_TEXT_SCALE
import com.cursorandroid.app.ui.theme.MIN_TEXT_SCALE
import com.cursorandroid.app.ui.theme.ThemeColorPresets
import com.cursorandroid.app.ui.theme.UiFont
import com.cursorandroid.app.ui.theme.clampTextScale
import com.cursorandroid.app.ui.theme.formatHexColor
import com.cursorandroid.app.ui.theme.parseHexColor
import com.cursorandroid.app.ui.thread.MessageText

private enum class AppearanceDialog { Font, CodeFont, Density }

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
    var scale by remember { mutableFloatStateOf(clampTextScale(store.textScalePct).toFloat()) }
    var hex by remember { mutableStateOf(formatHexColor(themeColor)) }
    var dialog by remember { mutableStateOf<AppearanceDialog?>(null) }
    val hexColor = parseHexColor(hex)

    fun applyColor(argb: Int) {
        themeColor = argb
        store.themeColor = argb
        onChanged()
    }

    ThemePreview()

    Text(
        "Accent color",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemeColorPresets.forEach { color ->
            val selected = themeColor == color
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    )
                    .clickable {
                        hex = formatHexColor(color)
                        applyColor(color)
                    },
            )
        }
    }
    OutlinedTextField(
        value = hex,
        onValueChange = { value ->
            hex = value.take(7)
            parseHexColor(hex)?.let { applyColor(it) }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        label = { Text("Custom color") },
        placeholder = { Text("#F54E00") },
        isError = hexColor == null,
        supportingText = { Text(if (hexColor == null) "Use #RRGGBB" else "Applies as you type") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
    )
    HorizontalDivider()

    SettingsChoiceRow(
        title = "Font",
        summary = uiFont.label,
        onClick = { dialog = AppearanceDialog.Font },
    )
    SettingsChoiceRow(
        title = "Code font",
        summary = codeFont.label,
        onClick = { dialog = AppearanceDialog.CodeFont },
    )
    SettingsChoiceRow(
        title = "Chat density",
        summary = "${density.label} · ${density.summary}",
        onClick = { dialog = AppearanceDialog.Density },
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
        AppearanceDialog.Font -> ChoiceDialog(
            title = "Font",
            selected = uiFont.id,
            options = UiFont.entries.map { it.id to it.label },
            onDismiss = { dialog = null },
            onPick = { id ->
                uiFont = UiFont.fromId(id)
                store.uiFont = uiFont.id
                dialog = null
                onChanged()
            },
        )
        AppearanceDialog.CodeFont -> ChoiceDialog(
            title = "Code font",
            selected = codeFont.id,
            options = CodeFont.entries.map { it.id to it.label },
            onDismiss = { dialog = null },
            onPick = { id ->
                codeFont = CodeFont.fromId(id)
                store.codeFont = codeFont.id
                dialog = null
                onChanged()
            },
        )
        AppearanceDialog.Density -> ChoiceDialog(
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
        null -> Unit
    }
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
