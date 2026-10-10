package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ModelParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeLogicTest {
    @Test
    fun legacyGithubTokenBecomesOneForge() {
        val migrated = migrateForges(emptyList(), "ghp_example")
        assertEquals(1, migrated.size)
        assertEquals(ForgeKind.GITHUB.id, migrated[0].provider)
        assertEquals("https://api.github.com", migrated[0].apiUrl)
        assertEquals("ghp_example", migrated[0].token)
        assertEquals(migrated, migrateForges(migrated, "ghp_example"))
    }

    @Test
    fun replacingTheGithubTokenKeepsOtherForges() {
        val gitlab = normalizedForge(
            ForgeConnection(id = "gl", provider = ForgeKind.GITLAB.id, token = "gl"),
        )
        val next = upsertPublicGithub(listOf(gitlab, publicGithubForge("old")), "new")
        assertEquals("new", next.first { it.provider == ForgeKind.GITHUB.id }.token)
        assertEquals(1, next.count { it.provider == ForgeKind.GITHUB.id })
        assertTrue(next.any { it.id == "gl" })
        assertTrue(upsertPublicGithub(next, null).none { it.provider == ForgeKind.GITHUB.id })
    }

    @Test
    fun repoDefaultMatchesIgnoringGitSuffix() {
        val saved = listOf(
            RepoDefault(
                repoUrl = "https://github.com/Acme/App.git",
                modelId = "composer",
                modelParams = listOf(ModelParam("effort", "high")),
                branch = "develop",
            ),
        )
        val found = findRepoDefault(saved, "https://github.com/acme/app")
        assertEquals("composer", found?.modelId)
        assertEquals("develop", found?.branch)
        assertEquals("high", found?.modelParams?.single()?.value)
    }

    @Test
    fun promptPrefixIsAppliedOnce() {
        assertEquals("Use the repo style.\n\nFix the bug", prefixPrompt("Use the repo style.", "Fix the bug"))
        assertEquals("Fix the bug", prefixPrompt("  ", "Fix the bug"))
        assertEquals("Use the repo style.\n\nFix the bug", prefixPrompt("Use the repo style.", "Use the repo style.\n\nFix the bug"))
    }

    @Test
    fun mcpSelectionFallsBackToEveryEnabledServer() {
        val one = StoredMcpServer(id = "a", name = "Alpha", url = "https://example.com/a")
        val two = StoredMcpServer(id = "b", name = "Beta", url = "https://example.com/b")
        assertEquals(listOf("Alpha", "Beta"), mcpServersFor(listOf(one, two), null)!!.map { it.name })
        assertEquals(listOf("Beta"), mcpServersFor(listOf(one, two), listOf("b"))!!.map { it.name })
        assertNull(mcpServersFor(listOf(one, two), emptyList()))
    }

    @Test
    fun branchRequestsStayOnTheForgeHost() {
        val github = branchCall(publicGithubForge("token"), "https://github.com/acme/app")
        assertEquals("https://api.github.com/repos/acme/app/branches?per_page=100", github?.url)
        assertTrue(github?.headers?.get("Authorization")?.startsWith("Bearer ") == true)
        val gitlab = normalizedForge(ForgeConnection(provider = ForgeKind.GITLAB_SELF.id, baseUrl = "https://git.example.com", token = "t"))
        val call = branchCall(gitlab, "https://git.example.com/acme/app")
        assertEquals(
            "https://git.example.com/api/v4/projects/acme%2Fapp/repository/branches?per_page=100",
            call?.url,
        )
        assertEquals("t", call?.headers?.get("PRIVATE-TOKEN"))
        assertEquals(
            "https://api.github.com/repos/acme/app/branches?per_page=100",
            branchCall(publicGithubForge("token"), "http://github.com/acme/app")?.url,
        )
        assertTrue(!callAllowed(publicGithubForge("token"), "http://api.github.com/user"))
        assertNull(testCall(ForgeConnection(provider = ForgeKind.ORIGIN.id, token = "t")))
    }

    @Test
    fun parsersReadBranchNamesAndLogins() {
        assertEquals(listOf("main", "dev"), parseBranchNames(ForgeKind.GITHUB.id, """[{"name":"main"},{"name":"dev"}]"""))
        assertEquals(
            listOf("main"),
            parseBranchNames(ForgeKind.AZURE.id, """{"value":[{"name":"refs/heads/main"}]}"""),
        )
        assertEquals("ada", parseForgeLogin("""{"login":"ada"}"""))
    }

    @Test
    fun selfHostedForgeMatchesItsBaseHost() {
        val forge = normalizedForge(
            ForgeConnection(provider = ForgeKind.GITEA.id, baseUrl = "https://git.example.com", token = "t"),
        )
        assertEquals(forge.apiUrl, forgeForRepo(listOf(forge), "https://git.example.com/acme/app")?.apiUrl)
        assertNull(forgeForRepo(listOf(forge), "https://github.com/acme/app"))
        assertEquals(
            ForgeKind.GITLAB.id,
            forgeForLabel(
                listOf(
                    normalizedForge(ForgeConnection(provider = ForgeKind.GITLAB.id, token = "gl")),
                    normalizedForge(ForgeConnection(provider = ForgeKind.GITLAB_SELF.id, baseUrl = "https://git.example.com", token = "self")),
                ),
                "GitLab",
            )?.provider,
        )
    }
}
