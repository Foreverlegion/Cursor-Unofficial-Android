package com.cursorandroid.app.ui.thread

import android.app.Application
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class BottomAnchoredListTest {
    @get:Rule
    val compose = createComposeRule()

    private val viewport = 400

    @Test
    fun reversedListKeepsTheTailOnTheBottomEdgeInTheSameFrameAsTheGrowth() {
        var grow by mutableIntStateOf(100)
        lateinit var state: LazyListState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state = rememberLazyListState()
            LazyColumn(
                state = state,
                reverseLayout = true,
                modifier = Modifier.fillMaxWidth().height(viewport.dp),
            ) {
                item(key = LIVE_TAIL_KEY) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                item(key = "streaming") { Box(Modifier.fillMaxWidth().height(grow.dp)) }
                items(20, key = { "old-$it" }) { Box(Modifier.fillMaxWidth().height(60.dp)) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        repeat(25) { step ->
            val size = 100 + (step + 1) * 14
            compose.runOnUiThread { grow = size; Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            assertEquals("step $step: growth applied", size, state.layoutInfo.visibleItemsInfo.first { it.key == "streaming" }.size)
            val tail = state.layoutInfo.visibleItemsInfo.first { it.key == LIVE_TAIL_KEY }
            assertEquals("step $step: tail sits on the bottom edge with no scroll call", 0, tail.offset)
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun forwardListNeedsAScrollCallAfterTheSameGrowth() {
        var grow by mutableIntStateOf(100)
        lateinit var state: LazyListState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state = rememberLazyListState()
            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(viewport.dp)) {
                items(20, key = { "old-$it" }) { Box(Modifier.fillMaxWidth().height(60.dp)) }
                item(key = "streaming") { Box(Modifier.fillMaxWidth().height(grow.dp)) }
                item(key = LIVE_TAIL_KEY) { Box(Modifier.fillMaxWidth().height(40.dp)) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { runBlocking { state.scrollToBottom() } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()

        compose.runOnUiThread { grow = 240; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val tail = state.layoutInfo.visibleItemsInfo.lastOrNull { it.key == LIVE_TAIL_KEY }
        val overflow = if (tail == null) Int.MAX_VALUE else tail.offset + tail.size - viewport
        assertTrue("forward layout leaves the tail $overflow px off the bottom until a scroll call runs", overflow != 0)
    }

    @Test
    fun scrollingUpIsNotPulledBackWhileTheTailGrows() {
        var grow by mutableIntStateOf(100)
        lateinit var state: LazyListState
        compose.setContent {
            state = rememberLazyListState()
            LazyColumn(
                state = state,
                reverseLayout = true,
                modifier = Modifier.fillMaxWidth().height(viewport.dp),
            ) {
                item(key = LIVE_TAIL_KEY) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                item(key = "streaming") { Box(Modifier.fillMaxWidth().height(grow.dp)) }
                items(20, key = { "old-$it" }) { Box(Modifier.fillMaxWidth().height(60.dp)) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { runBlocking { state.scrollBy(500f) } }
        compose.waitForIdle()
        val anchorKey = state.layoutInfo.visibleItemsInfo.first().key
        val anchorOffset = state.layoutInfo.visibleItemsInfo.first().offset

        grow = 300
        compose.waitForIdle()
        val after = state.layoutInfo.visibleItemsInfo.first { it.key == anchorKey }
        assertEquals("rows the user is reading stay put", anchorOffset, after.offset)
        assertTrue("reader is off the bottom", state.firstVisibleItemIndex > 0)
    }

    @Test
    fun shortThreadsStayAtTheTopWhenTheArrangementSaysSo() {
        lateinit var state: LazyListState
        compose.setContent {
            state = rememberLazyListState()
            LazyColumn(
                state = state,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                modifier = Modifier.fillMaxWidth().height(viewport.dp),
            ) {
                item(key = "last") { Box(Modifier.fillMaxWidth().height(60.dp)) }
                item(key = "first") { Box(Modifier.fillMaxWidth().height(60.dp)) }
            }
        }
        compose.waitForIdle()
        val first = state.layoutInfo.visibleItemsInfo.first { it.key == "first" }
        val topEdge = viewport - first.offset - first.size
        assertEquals("a short thread starts at the top, not the bottom", 0, topEdge)
    }

    @Test
    fun aRowAddedAtTheBottomIsRevealedByScrollToBottom() {
        var extra by mutableIntStateOf(0)
        lateinit var state: LazyListState
        compose.setContent {
            state = rememberLazyListState()
            LazyColumn(
                state = state,
                reverseLayout = true,
                modifier = Modifier.fillMaxWidth().height(viewport.dp),
            ) {
                items(extra, key = { "new-$it" }) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                items(20, key = { "old-$it" }) { Box(Modifier.fillMaxWidth().height(60.dp)) }
            }
        }
        compose.waitForIdle()
        extra = 1
        compose.waitForIdle()
        assertTrue("the list keeps the old first row anchored", state.firstVisibleItemIndex == 1)
        compose.runOnIdle { runBlocking { state.scrollToBottom() } }
        compose.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }
}
