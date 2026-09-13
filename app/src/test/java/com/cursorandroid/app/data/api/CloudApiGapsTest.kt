package com.cursorandroid.app.data.api

import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.DraftSubagent
import com.cursorandroid.app.data.repo.toApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudApiGapsTest {
    @Test
    fun cloudCreateTargetUsesPrUrlAndDropsStartingRef() {
        val (_, repos) = cloudCreateTarget(
            fromSavedEnv = false,
            envName = "",
            repoUrl = "https://github.com/acme/app",
            startingRef = "main",
            prUrl = "https://github.com/acme/app/pull/12",
        )
        val repo = repos!!.single()
        assertEquals("https://github.com/acme/app", repo.url)
        assertEquals("https://github.com/acme/app/pull/12", repo.prUrl)
        assertNull(repo.startingRef)
    }

    @Test
    fun cloudCreateTargetKeepsBranchWithoutPr() {
        val (_, repos) = cloudCreateTarget(false, "", "https://github.com/acme/app", "develop")
        val repo = repos!!.single()
        assertEquals("develop", repo.startingRef)
        assertNull(repo.prUrl)
    }

    @Test
    fun modelDefaultParamsPreferDefaultVariant() {
        val model = ModelItem(
            id = "composer-2",
            variants = listOf(
                ModelVariant(params = listOf(ModelParam("fast", "false")), displayName = "Slow"),
                ModelVariant(params = listOf(ModelParam("fast", "true")), displayName = "Fast", isDefault = true),
            ),
        )
        assertEquals(listOf(ModelParam("fast", "true")), model.defaultParams())
        val picked = model.selection(listOf(ModelParam("fast", "true")))
        assertEquals("composer-2", picked.id)
        assertEquals("true", picked.params!!.single().value)
    }

    @Test
    fun draftSubagentsMigrateLegacyFields() {
        val draft = ChatDraft(subName = "reviewer", subDesc = "Review diffs", subPrompt = "Be strict")
        val items = draft.resolvedSubagents()
        assertEquals(1, items.size)
        assertEquals("reviewer", items.single().name)
        val api = items.toApi()!!
        assertEquals("reviewer", api.single().name)
        assertNull(api.single().model)
    }

    @Test
    fun draftSubagentsHonorExplicitModel() {
        val api = listOf(
            DraftSubagent("reviewer", "Review diffs", "Be strict", "inherit"),
        ).toApi()!!
        assertEquals("inherit", api.single().model)
    }

    @Test
    fun workerPoolLineShowsLoad() {
        val pool = WorkerPool(
            poolName = "gpu",
            connectedWorkerCount = 2,
            inUseWorkerCount = 1,
            repoOwner = "acme",
            repoName = "app",
        )
        assertEquals(1, pool.idle())
        assertTrue(pool.line().contains("gpu"))
        assertTrue(pool.line().contains("1/2 busy"))
    }
}
