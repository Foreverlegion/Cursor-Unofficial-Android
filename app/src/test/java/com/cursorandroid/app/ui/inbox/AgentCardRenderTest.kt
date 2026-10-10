package com.cursorandroid.app.ui.inbox

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.ui.status.PlayColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real list row (swipe wrapper plus card) to a bitmap and checks pixels. 1 dp is 1 px
 * here (mdpi), so the card's outer corner sits at x = 12, y = 5.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class AgentCardRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val listBackground = Color(0xFF0B0B0D)
    private val agent = AgentSummary(
        id = "bc-card",
        name = "Tesla home-screen widget",
        status = "FINISHED",
        updatedAt = "2026-10-10T09:00:00Z",
    )

    private fun setRow(
        compact: Boolean,
        selected: Boolean = false,
        pinned: Boolean = false,
        shown: AgentSummary = agent,
    ) {
        compose.setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .testTag("row")
                        .width(360.dp)
                        .background(listBackground),
                ) {
                    SwipeArchiveRow(enabled = true, onArchive = {}) {
                        AgentRow(
                            agent = shown,
                            title = null,
                            git = null,
                            needsApproval = false,
                            selected = selected,
                            favorite = false,
                            hidden = false,
                            muted = false,
                            selecting = false,
                            checked = false,
                            compact = compact,
                            pinned = pinned,
                            longPressMenu = true,
                            onClick = {},
                            onLongClick = {},
                            onToggleFavorite = {},
                            onToggleMute = {},
                            onRename = {},
                            onHide = {},
                            onUnhide = {},
                            onArchive = {},
                            onUnarchive = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pixels(): android.graphics.Bitmap {
        val node = compose.onNodeWithTag("row").fetchSemanticsNode()
        val bounds = node.boundsInWindow
        val decor = compose.activity.window.decorView
        val full = android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(full))
        return android.graphics.Bitmap.createBitmap(
            full,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt(),
            bounds.height.toInt(),
        )
    }

    private fun android.graphics.Bitmap.at(x: Int, y: Int): Int = getPixel(x, y)

    private fun assertBackground(bitmap: android.graphics.Bitmap, x: Int, y: Int, what: String) {
        assertEquals(what, listBackground.toArgb(), bitmap.at(x, y))
    }

    private fun assertRoundedWithNothingBehind(bitmap: android.graphics.Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val left = 12
        val right = w - 12 - 1
        val top = 5
        val bottom = h - 5 - 1
        assertBackground(bitmap, left, top, "top-left corner")
        assertBackground(bitmap, right, top, "top-right corner")
        assertBackground(bitmap, left, bottom, "bottom-left corner")
        assertBackground(bitmap, right, bottom, "bottom-right corner")
        for (x in listOf(0, 3, 8, 11)) assertBackground(bitmap, x, h / 2, "left gutter x=$x")
        for (x in listOf(w - 1, w - 4, w - 9, w - 12)) assertBackground(bitmap, x, h / 2, "right gutter x=$x")
        for (y in listOf(0, 2, 4)) assertBackground(bitmap, w / 2, y, "top gutter y=$y")
        for (y in listOf(h - 1, h - 3, h - 5)) assertBackground(bitmap, w / 2, y, "bottom gutter y=$y")
        val archive = MaterialTheme_secondaryContainer.toArgb()
        for (y in 0 until h) for (x in 0 until w) {
            assertFalse("swipe background drawn at rest ($x,$y)", bitmap.at(x, y) == archive)
        }
    }

    private val MaterialTheme_secondaryContainer: Color
        get() = androidx.compose.material3.lightColorScheme().secondaryContainer

    @Test
    fun compactCardAtRestIsOneRoundedSurfaceWithNothingBehindIt() {
        setRow(compact = true)
        val bitmap = pixels()
        assertRoundedWithNothingBehind(bitmap)
        assertEquals(PlayColors.Card.toArgb(), bitmap.at(15, bitmap.height / 2))
        assertEquals(PlayColors.Card.toArgb(), bitmap.at(bitmap.width / 2, 8))
    }

    @Test
    fun selectedAndPinnedCardsStayRoundedToo() {
        setRow(compact = true, selected = true, pinned = true)
        val bitmap = pixels()
        assertRoundedWithNothingBehind(bitmap)
        assertEquals(0xFF24302E.toInt(), bitmap.at(15, bitmap.height / 2))
    }

    @Test
    fun fullCardAtRestHasNothingBehindIt() {
        setRow(compact = false)
        assertRoundedWithNothingBehind(pixels())
    }

    @Test
    fun swipeBackgroundOnlyAppearsWhileSwipingAndIsRoundedLikeTheCard() {
        setRow(compact = true)
        compose.onNodeWithTag("row").performTouchInput {
            down(center)
            moveBy(Offset(-140f, 0f))
        }
        compose.waitForIdle()
        val bitmap = pixels()
        val w = bitmap.width
        val h = bitmap.height
        val archive = MaterialTheme_secondaryContainer.toArgb()
        assertEquals("revealed Archive area", archive, bitmap.at(w - 12 - 3, h / 2))
        assertBackground(bitmap, w - 12 - 1, 5, "revealed area top-right corner is rounded")
        assertBackground(bitmap, w - 12 - 1, h - 5 - 1, "revealed area bottom-right corner is rounded")
        assertBackground(bitmap, w - 6, h / 2, "no archive color in the gutter")
        compose.onNodeWithTag("row").performTouchInput { up() }
    }

    private val longTitle = "Tesla home-screen widget with the resize handle and release build"

    private fun running(name: String) = agent.copy(name = name, status = "RUNNING", updatedAt = java.time.Instant.now().minusSeconds(35 * 60L).toString())

    private fun textOverflowed(text: String): Boolean {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().hasVisualOverflow
    }

    @Test
    fun overflowButtonEndsTheTitleRow() {
        setRow(compact = true)
        val title = compose.onNodeWithText("Tesla home-screen widget", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithContentDescription("More", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        assertTrue("overflow starts to the right of the title", more.left >= title.right - 1f)
        assertTrue("overflow center is inside the title row", more.center.y in title.top..title.bottom + 8f)
        assertTrue("overflow is the last thing on the line, ${more.right} of ${root.right}", more.right >= root.right - 24f)
    }

    @Test
    fun statusChipAndTimeSitOnASecondLineUnderTheTitle() {
        setRow(compact = true, shown = running("Tesla home-screen widget"))
        val title = compose.onNodeWithText("Tesla home-screen widget", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val chip = compose.onNodeWithText("Running", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithContentDescription("More", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("35m ago", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("chip is below the title (chip top ${chip.top}, title bottom ${title.bottom})", chip.top >= title.bottom - 1f)
        assertTrue("chip is below the overflow button", chip.top >= more.bottom - 4f)
        assertEquals("chip lines up with the title's left edge", title.left, chip.left - 10f, 2f)
        assertTrue("time follows the chip on the same line", time.left >= chip.right && time.center.y in chip.top..chip.bottom)
    }

    @Test
    fun theTitleKeepsEveryPixelUpToTheOverflowButton() {
        setRow(compact = true, shown = running(longTitle))
        val more = compose.onNodeWithContentDescription("More", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(longTitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("title width ${title.width} reaches the button at ${more.left}", title.right >= more.left - 8f)
        assertTrue("a long title is the only thing ellipsized on its line", textOverflowed(longTitle))
        val chip = compose.onNodeWithText("Running", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the chip is whole", chip.width >= 40f)
    }

    @Test
    fun aTitleThatFitsTheOldNarrowSlotIsNotCutOffAnymore() {
        setRow(compact = true, shown = running("Tesla home-screen widget"))
        assertFalse("title fits on one line with the chip below it", textOverflowed("Tesla home-screen widget"))
    }

    @Test
    fun compactCardStaysCompactWithTwoLines() {
        setRow(compact = true, shown = running("Tesla home-screen widget"))
        val root = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        assertTrue("compact card at most 82dp including its 10dp gutters, was ${root.height}", root.height <= 82f)
    }

    @Test
    fun fullCardKeepsTheSameTwoLineHeader() {
        setRow(compact = false, shown = running("Tesla home-screen widget"))
        val title = compose.onNodeWithText("Tesla home-screen widget", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val chip = compose.onNodeWithText("Running", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("chip below title", chip.top >= title.bottom - 1f)
    }
}
