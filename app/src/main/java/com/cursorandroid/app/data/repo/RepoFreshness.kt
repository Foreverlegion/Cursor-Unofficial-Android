package com.cursorandroid.app.data.repo

data class GitCommitTip(
    val sha: String,
    val title: String,
    val ref: String,
) {
    fun shortSha(): String = sha.take(7)
}

data class RepoBehind(
    val repoUrl: String,
    val branch: String,
    val chatSha: String,
    val chatTitle: String,
    val remoteSha: String,
    val remoteTitle: String,
    val behindBy: Int,
) {
    fun pullPrompt(): String {
        val count = if (behindBy > 0) "$behindBy commits" else "newer commits"
        return "This checkout is behind $branch " +
            "(${chatSha.take(7)} vs ${remoteSha.take(7)}, $count). " +
            "Fetch origin and fast-forward to the latest commit on $branch before any other work. " +
            "Do not edit files until HEAD matches $branch. Confirm the new HEAD when done."
    }
}

fun looksLikeGitSha(value: String): Boolean {
    return value.matches(Regex("[0-9a-fA-F]{7,40}")) && !value.contains('/')
}
