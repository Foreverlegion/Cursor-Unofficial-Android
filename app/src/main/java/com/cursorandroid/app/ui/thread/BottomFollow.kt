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
 *
 * Streaming must not call [scrollToItem] on every token. That restart fought the layout pass and
 * moved rows the user was not touching. Growth is applied once, from the anchor item's size.
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
 * Wires [follow] to its list. New tokens change the anchor item's height. They do not restart a scroll.
 */
@Composable
internal fun BottomFollowEffects(follow: BottomFollow) {
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

    // The newest rows are anchored to the bottom edge, so text appended below the viewport pushes
    // everything the user is reading. While not following, re-add that growth to the offset.
    // While following, correct only if the anchor has drifted. Do not scroll on an unchanged frame.
    LaunchedEffect(follow) {
        var lastKey: Any? = null
        var lastSize = -1
        snapshotFlow {
            val index = listState.firstVisibleItemIndex
            val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            anchor?.let { it.key to it.size }
        }.collect { now ->
            val beforeKey = lastKey
            val beforeSize = lastSize
            lastKey = now?.first
            lastSize = now?.second ?: -1
            if (follow.programmatic || now == null || beforeKey == null || beforeKey != now.first || beforeSize < 0) {
                return@collect
            }
            val grew = now.second - beforeSize
            if (grew == 0) return@collect
            if (follow.stickToBottom) {
                if (!listState.isScrollInProgress &&
                    (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)
                ) {
                    follow.programmatic = true
                    try {
                        listState.scrollToBottom()
                    } finally {
                        follow.programmatic = false
                    }
                }
            } else if (grew > 0 && !listState.isScrollInProgress) {
                follow.programmatic = true
                try {
                    listState.dispatchRawDelta(grew.toFloat())
                } finally {
                    follow.programmatic = false
                }
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
