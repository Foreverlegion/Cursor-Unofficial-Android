package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.auth.DashboardPeriodUsage
import com.cursorandroid.app.ui.status.PlayColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun DashboardUsageBars(usage: DashboardPeriodUsage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        usage.cursorModelsPercent?.let { percent ->
            UsageBarRow(label = "Cursor models", percent = percent)
        }
        usage.otherModelsPercent?.let { percent ->
            UsageBarRow(label = "Other models", percent = percent)
        }
        billingResetLabel(usage.billingCycleEndMs)?.let { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = PlayColors.Muted,
            )
        }
    }
}

@Composable
private fun UsageBarRow(label: String, percent: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label · ${percent.roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LinearProgressIndicator(
            progress = { (percent.coerceIn(0.0, 100.0) / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = PlayColors.Teal,
            trackColor = PlayColors.Card,
        )
    }
}

internal fun billingResetLabel(endMs: Long?): String? {
    if (endMs == null || endMs <= 0L) return null
    val date = Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val formatted = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(date)
    return "Resets $formatted"
}
