package com.cursorandroid.app.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object PlayColors {
    val Card = Color(0xFF1C1C22)
    val Teal = Color(0xFF2DD4BF)
    val TealInk = Color(0xFF042F2E)
    val Muted = Color(0xFF9A9AA3)
    val UserBubble = Color(0xFF6D5CEB)
    val AgentBubble = Color(0xFF2A2A32)
    val DoneFg = Color(0xFF3DDC97)
    val DoneBg = Color(0xFF123524)
    val RunFg = Color(0xFF2EE6C8)
    val RunBg = Color(0xFF0E3A34)
    val ApprovalFg = Color(0xFFF5B042)
    val ApprovalBg = Color(0xFF3A2A12)
    val FailFg = Color(0xFFFF6B6B)
    val FailBg = Color(0xFF3A1618)
}

fun RunIndicator.foreground(): Color = when (this) {
    RunIndicator.Done -> PlayColors.DoneFg
    RunIndicator.Running -> PlayColors.RunFg
    RunIndicator.NeedsApproval -> PlayColors.ApprovalFg
    RunIndicator.Failed -> PlayColors.FailFg
}

fun RunIndicator.background(): Color = when (this) {
    RunIndicator.Done -> PlayColors.DoneBg
    RunIndicator.Running -> PlayColors.RunBg
    RunIndicator.NeedsApproval -> PlayColors.ApprovalBg
    RunIndicator.Failed -> PlayColors.FailBg
}

@Composable
fun StatusPill(indicator: RunIndicator, modifier: Modifier = Modifier) {
    Text(
        indicator.label,
        color = indicator.foreground(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(indicator.background())
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

fun PrCheckChipKind.foreground(): Color = when (this) {
    PrCheckChipKind.ChecksRunning -> PlayColors.Muted
    else -> PlayColors.FailFg
}

fun PrCheckChipKind.background(): Color = when (this) {
    PrCheckChipKind.ChecksRunning -> Color(0xFF2A2A32)
    else -> PlayColors.FailBg
}

@Composable
fun PrCheckChip(
    snap: PrCheckSnap,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val kind = snap.chipKind ?: return
    Text(
        snap.label.ifBlank { kind.categoryPrefix },
        color = kind.foreground(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(kind.background())
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrCheckFailuresSheet(
    snap: PrCheckSnap,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val items = snap.failures.filter { it.label.isNotBlank() || it.checkName.isNotBlank() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Failed checks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (items.isEmpty()) {
                Text(
                    snap.label,
                    color = PlayColors.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = Color(0xFF2A2A32))
                    TextButton(
                        onClick = { onOpenUrl(prCheckItemUrl(item, snap.prUrl)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            item.label.ifBlank {
                                item.chipKind?.let { kind ->
                                    PrCheckNaming.failureLabel(kind, item.checkName, item.detail.takeIf { it.isNotBlank() })
                                }.orEmpty()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF2A2A32))
            TextButton(
                onClick = { onOpenUrl(prChecksOpenUrl(snap.prUrl)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("All checks on pull request")
            }
        }
    }
}

@Composable
fun PrCheckChipWithSheet(
    snap: PrCheckSnap,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
) {
    var sheet by remember { mutableStateOf(false) }
    PrCheckChip(
        snap = snap,
        modifier = modifier,
        onClick = {
            if (snap.chipKind == PrCheckChipKind.ChecksRunning) {
                onOpenUrl(prChecksOpenUrl(snap.prUrl))
            } else {
                sheet = true
            }
        },
    )
    if (sheet) {
        PrCheckFailuresSheet(
            snap = snap,
            onDismiss = { sheet = false },
            onOpenUrl = { url ->
                sheet = false
                onOpenUrl(url)
            },
        )
    }
}
