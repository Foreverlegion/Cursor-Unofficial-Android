package com.cursorandroid.app.ui.composeAgent

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.repo.ForgeConnection
import com.cursorandroid.app.data.repo.ForgeKind
import com.cursorandroid.app.data.repo.ForgeSyncStatus
import com.cursorandroid.app.data.repo.ForgeSyncStore
import com.cursorandroid.app.data.repo.publicGithubForge
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ForgeSourceListTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val cacheDir get() = File(context.filesDir, "forge-sync")

    @Before
    fun init() {
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    }

    @After
    fun clean() {
        ForgeSyncStore(cacheDir).clear()
    }

    @Test
    fun syncedForgeReposAppearInTheSourceListWithTheirSyncLine() {
        val c = AppContainer(context).also { it.store.demoMode = true }
        c.store.saveForges(
            listOf(
                publicGithubForge("ghp_x", id = "gh"),
                ForgeConnection(id = "gl", provider = ForgeKind.GITLAB.id, token = "glpat-x"),
            ),
        )
        val now = System.currentTimeMillis()
        val cache = ForgeSyncStore(cacheDir)
        cache.save(
            ForgeSyncStatus("gl", now, now - 3 * 60_000, repoCount = 1),
            listOf(RepositoryItem("https://gitlab.com/grp/only-on-gitlab.git", "gitlab", "main", "grp/only-on-gitlab")),
        )
        cache.save(
            ForgeSyncStatus("gh", now, error = "GitHub rejected the token (401). Check it in Settings > Forges.", authFailed = true),
            emptyList(),
        )
        c.forgeSync.load()

        compose.setContent {
            MaterialTheme {
                NewAgentScreen(container = c, showBack = false, onBack = {}, onCreated = {})
            }
        }
        compose.waitForIdle()

        val sources = compose.onAllNodesWithText("GitHub", substring = true)
        assertTrue(sources.fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("GitHub · Not synced yet · GitHub rejected the token (401). Check it in Settings > Forges.")
            .assertIsDisplayed()
    }
}
