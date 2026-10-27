package com.cursorandroid.app.ui.thread

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A bottom-up chat list whose newest row (the live thinking block) grows by appending lines at its end.
 * Once the user scrolls up the list must not move, whether they are reading history or inside the block.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class BottomFollowTest {
    @get:Rule
    val compose = createComposeRule()

    private var lines by mutableIntStateOf(30)
    private lateinit var listState: LazyListState
    private lateinit var follow: BottomFollow

    private fun show(tailLines: Int, history: Int = 30) {
        lines = tailLines
        compose.setContent {
            val state = remember { LazyListState() }
            val f = rememberBottomFollow("agent", state)
            listState = state
            follow = f
            BottomFollowEffects(f)
            Box(Modifier.fillMaxWidth().height(400.dp).testTag("list")) {
                LazyColumn(state = state, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                    item(key = "tail") {
                        Column {
                            repeat(lines) { i -> Text("thinking line $i", Modifier.height(40.dp).testTag("tail-$i")) }
                        }
                    }
                    items((0 until history).toList(), key = { "h$it" }) { i ->
                        Text("history $i", Modifier.height(60.dp).testTag("h-$i"))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun top(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun userScrollsUp() {
        compose.onNodeWithTag("list").performTouchInput {
            
            down(center)
            repeat(3) { moveBy(androidx.compose.ui.geometry.Offset(0f, 50f), delayMillis = 300) }
            advanceEventTime(400)
            up()
        }
        compose.waitForIdle()
    }

    private fun stream(more: Int) {
        repeat(more) {
            compose.runOnIdle { lines += 1 }
            compose.waitForIdle()
        }
    }

    @Test
    fun followsNewTextWhileAtTheBottom() {
        show(tailLines = 30)
        assertTrue(follow.stickToBottom)
        stream(10)
        assertTrue(follow.stickToBottom)
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
        assertTrue("older rows sit under the header", listState.canScrollForward)
        assertFalse(listState.canScrollBackward)
        val lastTop = top("tail-39")
        assertTrue("newest line is on screen, top=$lastTop", lastTop >= 0f && lastTop < 400f)
    }

    @Test
    fun scrolledUpInsideTheThinkingBlockStreamingDoesNotMoveTheViewport() {
        show(tailLines = 30)
        userScrollsUp()
        assertFalse("user left the bottom", follow.stickToBottom)
        assertEquals("still inside the thinking block", 0, listState.firstVisibleItemIndex)
        assertTrue(listState.firstVisibleItemScrollOffset > 80)
        val anchor = top("tail-0")
        stream(15)
        assertFalse(follow.stickToBottom)
        assertEquals("text under the finger must not move", anchor, top("tail-0"), 1f)
    }

    @Test
    fun scrolledUpIntoHistoryStreamingDoesNotMoveTheViewport() {
        show(tailLines = 3)
        userScrollsUp()
        userScrollsUp()
        assertFalse(follow.stickToBottom)
        assertTrue(listState.firstVisibleItemIndex > 0)
        val visible = listState.layoutInfo.visibleItemsInfo.first { it.index > 0 }
        val anchor = top("h-${(visible.key as String).drop(1)}")
        stream(15)
        assertFalse(follow.stickToBottom)
        assertEquals(anchor, top("h-${(visible.key as String).drop(1)}"), 1f)
    }

    @Test
    fun scrolledUpHistoryStaysPutWhenALowerReplyGrows() {
        var replyLines by mutableIntStateOf(2)
        compose.setContent {
            val state = remember { LazyListState() }
            val f = rememberBottomFollow("agent", state)
            listState = state
            follow = f
            BottomFollowEffects(f)
            Box(Modifier.fillMaxWidth().height(400.dp).testTag("list")) {
                LazyColumn(state = state, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                    item(key = "tail") {
                        Text("tail", Modifier.height(40.dp))
                    }
                    item(key = "reply") {
                        Column {
                            repeat(replyLines) { i ->
                                Text("reply $i", Modifier.height(28.dp).testTag("reply-$i"))
                            }
                        }
                    }
                    items((0 until 40).toList(), key = { "h$it" }) { i ->
                        Text("history $i", Modifier.height(60.dp).testTag("h-$i"))
                    }
                }
            }
        }
        compose.waitForIdle()
        userScrollsUp()
        userScrollsUp()
        assertFalse(follow.stickToBottom)
        assertTrue(listState.firstVisibleItemIndex > 1)
        val visible = listState.layoutInfo.visibleItemsInfo.first { (it.key as String).startsWith("h") }
        val tag = "h-${(visible.key as String).drop(1)}"
        val anchor = top(tag)
        repeat(8) {
            compose.runOnIdle { replyLines += 1 }
            compose.waitForIdle()
        }
        assertFalse(follow.stickToBottom)
        assertEquals(anchor, top(tag), 1f)
    }

    @Test
    fun jumpToBottomFollowsAgainAfterTheUserWasUp() {
        show(tailLines = 30)
        userScrollsUp()
        assertFalse(follow.stickToBottom)
        compose.runOnIdle { kotlinx.coroutines.runBlocking { follow.snapToBottom() } }
        compose.waitForIdle()
        assertTrue(follow.stickToBottom)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
        stream(5)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }
}
