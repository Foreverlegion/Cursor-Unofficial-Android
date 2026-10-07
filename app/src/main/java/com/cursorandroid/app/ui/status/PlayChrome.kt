package com.cursorandroid.app.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
