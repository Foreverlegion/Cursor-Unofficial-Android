package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.CreateAgentRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRuleSyncTest {
    private val json = Json { explicitNulls = false; encodeDefaults = true }

    @Test
    fun directWriteAndOriginCreateAreOutOfReach() {
        assertFalse(UserRuleSync.DIRECT_WRITE)
        assertFalse(UserRuleSync.originCreateAuthorized())
        val create = UserRuleSync.originCreate("acme")
        assertEquals("POST", create.method)
        assertEquals("https://api.cursor.com/v1/origin/repos/acme", create.url)
        assertEquals(UserRuleSync.SCOPE, create.scope)
        assertEquals(UserRuleSync.REPO_NAME, create.body.name)
        assertEquals("main", create.body.defaultBranch)
        val body = json.encodeToString(UserRuleSync.OriginCreateBody.serializer(), create.body)
        assertEquals("""{"name":"cursor-unofficial-android","defaultBranch":"main"}""", body)
    }

    @Test
    fun originCloneUrlAcceptsBothShapes() {
        assertEquals(
            "https://origin.cursor.com/git/acme/rocket.git",
            UserRuleSync.originCloneUrl("https://origin.cursor.com/git/acme/rocket.git"),
        )
        assertEquals(
            "https://origin.cursor.com/git/acme/rocket.git",
            UserRuleSync.originCloneUrl("https://origin.cursor.com/acme/rocket"),
        )
        assertNull(UserRuleSync.originCloneUrl("https://github.com/acme/rocket.git"))
        assertNull(UserRuleSync.originCloneUrl("https://user:token@origin.cursor.com/git/acme/rocket.git"))
        assertNull(UserRuleSync.originCloneUrl("http://origin.cursor.com/git/acme/rocket.git"))
        assertNull(UserRuleSync.originCloneUrl("https://origin.cursor.com/git/acme/rocket.git?x=1"))
    }

    @Test
    fun agentRequestIsRepoBackedAndIdempotentInThePrompt() {
        val url = "https://origin.cursor.com/git/acme/cursor-unofficial-android.git"
        val body = UserRuleSync.agentRequest(url)
        assertEquals(url, body.repos!!.single().url)
        assertNull(body.repos!!.single().startingRef)
        assertNull(body.env)
        assertEquals(false, body.autoCreatePR)
        assertEquals(ProfileRule.AGENT_NAME, body.name)
        assertTrue(body.prompt.text.contains("replace its text"))
        assertTrue(body.prompt.text.contains("Do not add a second copy"))
        assertTrue(body.prompt.text.contains(UserRuleSync.ruleBody()))
        assertTrue(UserRuleSync.ruleBody().startsWith("### What this app is"))
        assertFalse(body.prompt.text.contains("both `repos` and `env` omitted"))
        val text = json.encodeToString(CreateAgentRequest.serializer(), body)
        assertTrue(text.contains("\"repos\""))
        assertFalse(text.contains("\"env\""))
        assertTrue(text.contains("\"autoCreatePR\":false"))
    }
}
