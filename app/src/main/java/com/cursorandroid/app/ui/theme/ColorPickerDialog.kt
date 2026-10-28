package com.cursorandroid.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    presetColors: List<Int>,
    allowDefault: Boolean = false,
    defaultPreviewColor: Int = DefaultThemeColor,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initialColor) { mutableIntStateOf(initialColor) }
    val startHsv = remember(initialColor, defaultPreviewColor) {
        argbToHsv(displayArgb(initialColor, defaultPreviewColor))
    }
    var hue by remember(initialColor) { mutableFloatStateOf(startHsv.hue) }
    var saturation by remember(initialColor) { mutableFloatStateOf(startHsv.saturation) }
    var value by remember(initialColor) { mutableFloatStateOf(startHsv.value) }
    var hex by remember(initialColor) {
        mutableStateOf(
            if (initialColor == 0 && allowDefault) {
                ""
            } else {
                formatHexColor(initialColor)
            },
        )
    }

    fun syncFromDraftArgb(argb: Int) {
        val hsv = argbToHsv(argb)
        hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
        hex = formatHexColor(argb)
    }

    fun applyCustom(h: Float, s: Float, v: Float) {
        val packed = hsvToArgb(h, s, v)
        draft = packed
        hue = h
        saturation = s
        value = v
        hex = formatHexColor(packed)
    }

    fun pickPreset(argb: Int) {
        draft = argb
        syncFromDraftArgb(argb)
    }

    fun pickDefault() {
        draft = 0
        val hsv = argbToHsv(defaultPreviewColor)
        hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
        hex = ""
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                ColorPreviewRow(
                    initialColor = initialColor,
                    draftColor = draft,
                    defaultPreviewColor = defaultPreviewColor,
                )
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val wheelSize = minOf(maxWidth, 280.dp).coerceAtLeast(160.dp)
                    HsvWheel(
                        size = wheelSize,
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onPick = { h, s -> applyCustom(h, s, value) },
                    )
                }
                Column(modifier = Modifier.semantics { contentDescription = "Brightness slider" }) {
                    Text(
                        "Brightness",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = value,
                        onValueChange = { v ->
                            value = v
                            applyCustom(hue, saturation, v)
                        },
                        valueRange = 0f..1f,
                    )
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { raw ->
                        hex = raw.take(7)
                        if (hex.isBlank() && allowDefault) {
                            pickDefault()
                            return@OutlinedTextField
                        }
                        parseHexColor(hex)?.let { parsed ->
                            draft = parsed
                            syncFromDraftArgb(parsed)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hex") },
                    placeholder = { Text(if (allowDefault) "Default" else "#RRGGBB") },
                    isError = hex.isNotEmpty() && parseHexColor(hex) == null,
                    supportingText = {
                        Text(
                            when {
                                hex.isEmpty() && allowDefault && draft == 0 -> "Default uses theme accent"
                                hex.isNotEmpty() && parseHexColor(hex) == null -> "Use #RRGGBB"
                                else -> "Type #RRGGBB without using the wheel"
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                if (presetColors.isNotEmpty()) {
                    Text(
                        "Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        presetColors.forEach { swatch ->
                            val selected = draft == swatch
                            val label = formatHexColor(swatch)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatch))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable { pickPreset(swatch) }
                                    .semantics { contentDescription = "Preset $label" },
                            )
                        }
                    }
                }
                if (allowDefault) {
                    TextButton(
                        onClick = { pickDefault() },
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Text(if (draft == 0) "Default (selected)" else "Default")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(draft) }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun ColorPreviewRow(
    initialColor: Int,
    draftColor: Int,
    defaultPreviewColor: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PreviewChip(
            label = "Current",
            argb = displayArgb(initialColor, defaultPreviewColor),
            modifier = Modifier.weight(1f),
        )
        PreviewChip(
            label = "New",
            argb = displayArgb(draftColor, defaultPreviewColor),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PreviewChip(label: String, argb: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(argb))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .semantics { contentDescription = "$label color ${formatHexColor(argb)}" },
            )
            Text(formatHexColor(argb), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HsvWheel(
    size: Dp,
    hue: Float,
    saturation: Float,
    value: Float,
    onPick: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val wheelBitmap = remember(px, value) { buildHsWheelBitmap(px, value) }
    Box(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = "Color wheel" }
            .pointerInput(px, value) {
                fun handle(x: Float, y: Float) {
                    val radius = px / 2f
                    hsFromWheelOffset(radius, radius, radius * 0.96f, x, y)?.let { picked ->
                        onPick(picked.hue, picked.saturation)
                    }
                }
                detectTapGestures { offset -> handle(offset.x, offset.y) }
                detectDragGestures { change, _ ->
                    change.consume()
                    handle(change.position.x, change.position.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = wheelBitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(modifier = Modifier.size(size)) {
            val radius = this.size.minDimension / 2f * 0.96f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val (mx, my) = wheelMarkerOffset(center.x, center.y, radius, HsvColor(hue, saturation, value))
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(mx, my),
            )
            drawCircle(
                color = Color.Black,
                radius = 8.dp.toPx(),
                center = Offset(mx, my),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = Color(hsvToArgb(hue, saturation, value)),
                radius = 5.dp.toPx(),
                center = Offset(mx, my),
            )
        }
    }
}

private fun buildHsWheelBitmap(diameter: Int, value: Float): androidx.compose.ui.graphics.ImageBitmap {
    val bmp = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
    val cx = diameter / 2f
    val radius = cx * 0.96f
    val pixels = IntArray(diameter * diameter)
    for (y in 0 until diameter) {
        for (x in 0 until diameter) {
            val idx = y * diameter + x
            val hs = hsFromWheelOffset(cx, cx, radius, x + 0.5f, y + 0.5f)
            pixels[idx] = if (hs == null) {
                0
            } else {
                hsvToArgb(hs.hue, hs.saturation, value)
            }
        }
    }
    bmp.setPixels(pixels, 0, diameter, 0, 0, diameter, diameter)
    return bmp.asImageBitmap()
}
