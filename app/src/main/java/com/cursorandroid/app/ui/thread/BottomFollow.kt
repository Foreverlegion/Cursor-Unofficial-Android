package com.cursorandroid.app.ui.thread

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Follow-the-latest behaviour for a bottom-up chat list (`reverseLayout = true`, index 0 is the newest row).
 *
 * The list follows new content only while the user is at or near the bottom. Once they scroll up
 * [stickToBottom] turns false, the list never scrolls by itself, and the jump-to-bottom button is shown.
 */
@Stable
internal class BottomFollow(val listState: LazyListState) {
    var stickToBottom by mutableStateOf(true)
        internal set
    internal var programmatic by mutableStateOf(false)

    suspend fun snapToBottom() {
        programmatic = true
        stickToBottom = true
        try {
            listState.scrollToBottom()
        } finally {
            programmatic = false
        }
    }
}

@Composable
internal fun rememberBottomFollow(key: Any, listState: LazyListState): BottomFollow =
    remember(key, listState) { BottomFollow(listState) }

/**
 * Wires [follow] to its list. [growKey] must change whenever rows are added or the newest row grows.
 */
@Composable
internal fun BottomFollowEffects(follow: BottomFollow, growKey: Any?) {
    val listState = follow.listState

    LaunchedEffect(follow) {
        snapshotFlow {
            listState.isScrollInProgress to listState.isPinnedToBottom()
        }.collect { (scrolling, pinned) ->
            if (follow.programmatic) return@collect
            if (scrolling && !pinned) follow.stickToBottom = false
            else if (!scrolling && pinned) follow.stickToBottom = true
        }
    }

    LaunchedEffect(follow, growKey, follow.stickToBottom) {
        if (follow.stickToBottom && !listState.isScrollInProgress) follow.snapToBottom()
    }

    // The newest row is anchored to the bottom edge, so text appended to it pushes everything the user
    // is reading upward. While not following, re-add the growth to the offset so the viewport stays put.
    LaunchedEffect(follow) {
        var last: Pair<Any, Int>? = null
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { it.key to it.size }
        }.collect { now ->
            val before = last
            last = now
            if (now == null || before == null || before.first != now.first) return@collect
            val grew = now.second - before.second
            if (grew > 0 && !follow.stickToBottom && !follow.programmatic) {
                listState.dispatchRawDelta(grew.toFloat())
            }
        }
    }
}

/** Re-anchors after rows are added or removed at the bottom, where the list would keep the previous first row in place. */
internal suspend fun LazyListState.scrollToBottom() {
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) scrollToItem(0)
}

/** True while the newest row's bottom is near the viewport bottom. */
internal fun LazyListState.isPinnedToBottom(thresholdPx: Int = 80): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= thresholdPx
