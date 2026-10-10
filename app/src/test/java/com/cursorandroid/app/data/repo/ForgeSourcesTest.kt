package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeSourcesTest {
    private val gitlab = ForgeConnection(id = "gl", provider = ForgeKind.GITLAB.id, token = "glpat-x")
    private val github = publicGithubForge("ghp_x")
    private val catalog = listOf(
        RepositoryItem(url = "https://github.com/acme/app", provider = "github"),
        RepositoryItem(url = "https://origin.cursor.com/acme/app", provider = "origin"),
    )

    @Test
    fun savedGitlabForgeAppearsAsSourceEvenWhenTheCatalogHasNoGitlabRepos() {
        assertEquals(listOf("GitHub", "Origin"), sourceLabels(catalog, emptyList()))
        assertEquals(listOf("GitHub", "GitLab", "Origin"), sourceLabels(catalog, listOf(github, gitlab)))
    }

    @Test
    fun everyForgeKindMapsToASource() {
        val forges = listOf(
            github,
            gitlab,
            ForgeConnection(id = "b", provider = ForgeKind.BITBUCKET.id, token = "t"),
            ForgeConnection(id = "a", provider = ForgeKind.AZURE.id, token = "t", username = "org"),
            ForgeConnection(id = "o", provider = ForgeKind.ORIGIN.id, token = "t", baseUrl = "https://origin.cursor.com"),
            ForgeConnection(id = "m", provider = ForgeKind.MANUAL.id, token = "t", baseUrl = "https://git.corp.example"),
        )
        assertEquals(
            listOf("GitHub", "GitLab", "Bitbucket", "Azure DevOps", "Origin", "git.corp.example"),
            sourceLabels(emptyList(), forges),
        )
    }

    @Test
    fun selfHostedGitlabAndEnterpriseGithubShareTheirSourceLabel() {
        val self = ForgeConnection(
            id = "s", provider = ForgeKind.GITLAB_SELF.id, token = "t", baseUrl = "https://git.corp.example",
        )
        val ghe = ForgeConnection(
            id = "e", provider = ForgeKind.GITHUB_ENTERPRISE.id, token = "t", baseUrl = "https://ghe.corp.example",
        )
        assertEquals(listOf("GitHub", "GitLab"), sourceLabels(emptyList(), listOf(self, ghe, gitlab, github)))
    }

    @Test
    fun forgeWithoutATokenIsNotASource() {
        assertTrue(sourceLabels(emptyList(), listOf(gitlab.copy(token = ""))).isEmpty())
    }

    @Test
    fun gitlabRepoListUsesMembershipSearchAndPaging() {
        val call = repoListCall(gitlab, "my app", 3)!!
        assertEquals("GET", call.method)
        assertEquals("glpat-x", call.headers["PRIVATE-TOKEN"])
        assertTrue(call.url.startsWith("https://gitlab.com/api/v4/projects?membership=true"))
        assertTrue(call.url.contains("page=3"))
        assertTrue(call.url.contains("per_page=100"))
        assertTrue(call.url.contains("search=my+app"))
        assertFalse(repoListCall(gitlab, "", 1)!!.url.contains("search="))
    }

    @Test
    fun selfHostedGitlabListsFromItsOwnApiHostOnly() {
        val self = ForgeConnection(
            id = "s", provider = ForgeKind.GITLAB_SELF.id, token = "t", baseUrl = "https://git.corp.example",
        )
        assertTrue(repoListCall(self, "", 1)!!.url.startsWith("https://git.corp.example/api/v4/projects"))
        val plain = self.copy(baseUrl = "http://git.corp.example")
        assertNull(repoListCall(plain, "", 1))
        assertNull(repoListCall(self.copy(token = ""), "", 1))
    }

    @Test
    fun gitlabProjectsParseToHttpsCloneUrlsUnderTheGitlabSource() {
        val body = """[
          {"id":1,"path_with_namespace":"grp/sub/app","default_branch":"main",
           "http_url_to_repo":"https://gitlab.com/grp/sub/app.git","web_url":"https://gitlab.com/grp/sub/app"},
          {"id":2,"path_with_namespace":"grp/ssh-only","ssh_url_to_repo":"git@gitlab.com:grp/ssh-only.git"},
          {"id":3,"path_with_namespace":"grp/other","http_url_to_repo":"https://gitlab.com/grp/other.git"}
        ]"""
        val repos = parseForgeRepos(gitlab, body)
        assertEquals(listOf("https://gitlab.com/grp/sub/app.git", "https://gitlab.com/grp/other.git"), repos.map { it.url })
        assertEquals("GitLab", repos[0].providerLabel())
        assertEquals("grp/sub/app", repos[0].displayName())
        assertEquals("main", repos[0].defaultBranch)
        assertEquals(listOf("GitLab"), sourceLabels(repos, emptyList()))
    }

    @Test
    fun selfHostedGitlabReposKeepTheGitlabLabelDespiteTheHostName() {
        val self = ForgeConnection(
            id = "s", provider = ForgeKind.GITLAB_SELF.id, token = "t", baseUrl = "https://git.corp.example",
        )
        val repos = parseForgeRepos(self, """[{"path_with_namespace":"a/b","http_url_to_repo":"https://git.corp.example/a/b.git"}]""")
        assertEquals("GitLab", repos.single().providerLabel())
    }

    @Test
    fun pageSizeDrivesWhetherMoreIsAvailable() {
        assertEquals(100, repoPageSize(ForgeKind.GITLAB.id))
        assertEquals(50, repoPageSize(ForgeKind.GITEA.id))
    }

    @Test
    fun githubBitbucketAndGiteaListingsParse() {
        val gh = parseForgeRepos(github, """[{"full_name":"o/r","clone_url":"https://github.com/o/r.git","default_branch":"dev"}]""")
        assertEquals("https://github.com/o/r.git", gh.single().url)
        assertEquals("GitHub", gh.single().providerLabel())
        val bb = parseForgeRepos(
            ForgeConnection(provider = ForgeKind.BITBUCKET.id, token = "t"),
            """{"values":[{"full_name":"w/r","mainbranch":{"name":"main"},"links":{"html":{"href":"https://bitbucket.org/w/r"}}}]}""",
        )
        assertEquals("https://bitbucket.org/w/r", bb.single().url)
        assertEquals("Bitbucket", bb.single().providerLabel())
        val gitea = ForgeConnection(provider = ForgeKind.GITEA.id, token = "t", baseUrl = "https://code.corp.example")
        val gt = parseForgeRepos(gitea, """{"ok":true,"data":[{"full_name":"o/r","clone_url":"https://code.corp.example/o/r.git"}]}""")
        assertEquals("code.corp.example", gt.single().providerLabel())
        assertTrue(parseForgeRepos(github, "not json").isEmpty())
    }

    @Test
    fun azureAndOriginFallBackToTheCatalog() {
        assertNull(repoListCall(ForgeConnection(provider = ForgeKind.AZURE.id, token = "t"), "", 1))
        assertFalse(forgeSupportsRepoList(ForgeConnection(provider = ForgeKind.ORIGIN.id, token = "t", baseUrl = "https://o.example")))
        assertTrue(forgeSupportsRepoList(gitlab))
    }

    @Test
    fun forgeReposMergeWithCatalogWithoutDuplicates() {
        val a = RepositoryItem(url = "https://gitlab.com/g/a", provider = "gitlab")
        val b = RepositoryItem(url = "https://gitlab.com/g/a.git", provider = "gitlab")
        val c = RepositoryItem(url = "https://gitlab.com/g/c.git", provider = "gitlab")
        assertEquals(listOf(a, c), mergeRepos(listOf(a), listOf(b, c)))
        assertEquals(listOf(c), filterRepos(listOf(a, c), "g/c"))
    }

    @Test
    fun theSourceForTheSavedForgeResolvesToItsToken() {
        val forges = listOf(github, gitlab)
        assertEquals("glpat-x", forgeForLabel(forges, "GitLab")!!.token)
        assertNotNull(forgeForLabel(forges, "GitHub"))
    }
}
