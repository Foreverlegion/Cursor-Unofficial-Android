package com.cursorandroid.app.ui.inbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One nesting level in the Agents folder/repo tree. */
internal val FolderTreeStepDp: Dp = 18.dp

internal val FolderTreeBaseHorizontalDp: Dp = 16.dp

fun folderTreeStartPadding(depth: Int): Dp {
    require(depth >= 0) { "depth must be non-negative" }
    return FolderTreeBaseHorizontalDp + FolderTreeStepDp * depth
}

fun folderTreeChildDepth(parentDepth: Int): Int {
    require(parentDepth >= 0) { "parentDepth must be non-negative" }
    return parentDepth + 1
}

fun folderTreeNestedGroupAgentDepth(groupDepth: Int): Int {
    require(groupDepth >= 0) { "groupDepth must be non-negative" }
    return groupDepth + 1
}

fun folderTreeRowA11y(depth: Int, parentFolderName: String?, selfLabel: String): String {
    if (depth <= 0 || parentFolderName.isNullOrBlank()) return selfLabel
    return "$selfLabel, in folder $parentFolderName"
}

/** Folder-level guide that should continue on deeper rows (e.g. chats inside a repo group). */
fun folderTreeExtraGuideDepths(rowDepth: Int, parentFolderName: String?): List<Int> {
    if (parentFolderName.isNullOrBlank()) return emptyList()
    val folderChildDepth = folderTreeChildDepth(0)
    if (rowDepth <= folderChildDepth) return emptyList()
    return listOf(folderChildDepth)
}

internal fun folderTreeGuideDepths(rowDepth: Int, parentFolderName: String?): List<Int> {
    val depths = mutableListOf<Int>()
    if (rowDepth > 0) depths += rowDepth
    depths += folderTreeExtraGuideDepths(rowDepth, parentFolderName)
    return depths.distinct().sorted()
}

/**
 * Indents a list row and draws a low-contrast vertical guide in the gutter for nested rows.
 */
@Composable
internal fun FolderTreeRow(
    depth: Int,
    parentFolderName: String? = null,
    rowLabel: String? = null,
    verticalPadding: Dp = 10.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
    val density = LocalDensity.current
    val a11y = rowLabel?.let { folderTreeRowA11y(depth, parentFolderName, it) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (a11y != null) {
                    Modifier.semantics { contentDescription = a11y }
                } else {
                    Modifier
                },
            ),
    ) {
        if (depth > 0) {
            Canvas(Modifier.matchParentSize()) {
                val stepPx = with(density) { FolderTreeStepDp.toPx() }
                val basePx = with(density) { FolderTreeBaseHorizontalDp.toPx() }
                for (guideDepth in folderTreeGuideDepths(depth, parentFolderName)) {
                    val lineX = basePx + (guideDepth - 1) * stepPx + stepPx / 2f
                    drawLine(
                        color = guideColor,
                        start = Offset(lineX, 0f),
                        end = Offset(lineX, size.height),
                        strokeWidth = with(density) { 1.dp.toPx() },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = folderTreeStartPadding(depth),
                    end = FolderTreeBaseHorizontalDp,
                    top = verticalPadding,
                    bottom = verticalPadding,
                ),
        ) {
            content()
        }
    }
}

@Composable
internal fun FolderTreeAgentInset(
    depth: Int,
    parentFolderName: String? = null,
    content: @Composable () -> Unit,
) {
    if (depth <= 0) {
        content()
        return
    }
    FolderTreeRow(
        depth = depth,
        parentFolderName = parentFolderName,
        verticalPadding = 0.dp,
    ) {
        content()
    }
}
