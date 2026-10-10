package com.cursorandroid.app.ui.thread

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.DemoSession
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h860dp-mdpi")
class ThreadInsetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun initWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    }

    private fun show() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context).also { it.store.demoMode = true }
        compose.runOnUiThread {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        }
        compose.setContent {
            MaterialTheme {
                ThreadScreen(
                    container = container,
                    agentId = DemoSession.NOTES,
                    showBack = true,
                    onBack = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun applyInsets(nav: Int, ime: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, nav))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime))
            .setVisible(WindowInsetsCompat.Type.ime(), ime > 0)
            .build()
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
    }

    private fun composerBottom(): Float =
        compose.onNodeWithTag("chat-composer").fetchSemanticsNode().boundsInWindow.bottom

    private fun windowHeight(): Float = compose.activity.window.decorView.height.toFloat()

    private fun check(label: String, expectHeight: Boolean = true) {
        val list = compose.onNodeWithTag("chat-list").fetchSemanticsNode().boundsInWindow
        val composer = compose.onNodeWithTag("chat-composer").fetchSemanticsNode().boundsInWindow
        assertTrue("$label: list bottom ${list.bottom} must be <= composer top ${composer.top}", list.bottom <= composer.top + 0.5f)
        if (expectHeight) assertTrue("$label: list has height ${list.height}", list.height > 40f)
    }

    private fun cycle(ime: Int, expectHeight: Boolean = true) {
        show()
        applyInsets(nav = 48, ime = 0)
        check("keyboard hidden", expectHeight)
        val hidden = composerBottom()
        assertTrue("composer clears the nav bar: $hidden vs ${windowHeight() - 48}", hidden <= windowHeight() - 48 + 0.5f)
        applyInsets(nav = 48, ime = ime)
        check("keyboard shown", expectHeight)
        val shown = composerBottom()
        assertTrue("composer sits above the keyboard: $shown vs ${windowHeight() - ime}", shown <= windowHeight() - ime + 0.5f)
        assertTrue("insets were applied", shown < hidden - 20f)
        applyInsets(nav = 48, ime = 0)
        check("keyboard hidden again", expectHeight)
        assertTrue("composer returns to rest", composerBottom() >= hidden - 0.5f)
    }

    @Test
    fun foldCoverPortrait() = cycle(ime = 300)

    @Test
    @Config(qualifiers = "w673dp-h841dp-mdpi")
    fun foldInnerScreen() = cycle(ime = 320)

    @Test
    @Config(qualifiers = "w860dp-h360dp-mdpi")
    fun landscapeNeverOverlapsEvenWhenTheKeyboardLeavesNoRoom() = cycle(ime = 150, expectHeight = false)
}
