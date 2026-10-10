package com.cursorandroid.app.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.ui.theme.LocalAppearance
import com.cursorandroid.app.ui.theme.family

internal data class TextSegment(val text: String, val code: Boolean)

/** Splits message text on ``` fences. An unclosed fence (a streaming message) runs to the end. */
internal fun splitCodeBlocks(text: String): List<TextSegment> {
    if (!text.contains("```")) return listOf(TextSegment(text, false))
    val out = ArrayList<TextSegment>()
    val lines = text.split('\n')
    val buf = StringBuilder()
    var inCode = false
    fun flush(code: Boolean) {
        val body = buf.toString().trim('\n')
        if (body.isNotBlank()) out += TextSegment(body, code)
        buf.setLength(0)
    }
    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            flush(inCode)
            inCode = !inCode
            continue
        }
        if (buf.isNotEmpty()) buf.append('\n')
        buf.append(line)
    }
    flush(inCode)
    return if (out.isEmpty()) listOf(TextSegment(text, false)) else out
}

@Composable
internal fun MessageText(text: String, color: Color) {
    val segments = remember(text) { splitCodeBlocks(text) }
    val appearance = LocalAppearance.current
    if (segments.size == 1 && !segments[0].code) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { segment ->
            if (segment.code) {
                Text(
                    segment.text,
                    color = color,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = appearance.codeFont.family()),
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            } else {
                Text(segment.text, color = color, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
