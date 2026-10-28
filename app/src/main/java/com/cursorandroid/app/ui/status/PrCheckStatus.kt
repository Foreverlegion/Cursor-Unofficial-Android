package com.cursorandroid.app.ui.status

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class PrCheckChipKind {
    ChecksRunning,
    BuildFailed,
    TestsFailed,
    ChecksFailed,
    ;

    val label: String
        get() = when (this) {
            ChecksRunning -> "Checks running"
            BuildFailed -> "Build failed"
            TestsFailed -> "Tests failed"
            ChecksFailed -> "Checks failed"
        }
}

@Serializable
data class PrCheckSnap(
    @SerialName("headSha") val headSha: String = "",
    @SerialName("prUrl") val prUrl: String = "",
    @SerialName("kind") val kind: String = "",
    @SerialName("failedCount") val failedCount: Int = 0,
    @SerialName("label") val label: String = "",
) {
    val chipKind: PrCheckChipKind?
        get() = kind.takeIf { it.isNotBlank() }?.let { runCatching { PrCheckChipKind.valueOf(it) }.getOrNull() }

    val showChip: Boolean get() = label.isNotBlank() && chipKind != null
}

data class PrCheckRunView(
    val name: String,
    val status: String,
    val conclusion: String?,
)

object PrCheckClassifier {
    private val BUILD = Regex("""(?i)(build|assemble|apk|preview-apk|gradle|packag|lint|android.?lint|compile)""")
    private val TEST = Regex("""(?i)(test|unit|instrument|robolectric|jest|pytest|mocha)""")

    fun category(name: String): PrCheckChipKind {
        val n = name.trim()
        if (TEST.containsMatchIn(n)) return PrCheckChipKind.TestsFailed
        if (BUILD.containsMatchIn(n)) return PrCheckChipKind.BuildFailed
        return PrCheckChipKind.ChecksFailed
    }
}

object PrCheckSummary {
    private val failedConclusions = setOf("failure", "cancelled", "timed_out", "action_required")

    fun summarize(runs: List<PrCheckRunView>): PrCheckDisplay? {
        if (runs.isEmpty()) return null
        val failed = runs.filter { run ->
            run.status.equals("completed", ignoreCase = true) &&
                run.conclusion?.lowercase() in failedConclusions
        }
        val pending = runs.any { run ->
            !run.status.equals("completed", ignoreCase = true) &&
                !run.status.equals("waiting", ignoreCase = true)
        }
        if (failed.isEmpty()) {
            return if (pending) PrCheckDisplay(PrCheckChipKind.ChecksRunning, failedCount = 0) else null
        }
        val categories = failed.map { PrCheckClassifier.category(it.name) }.distinct()
        val kind = when {
            categories.size == 1 -> categories.single()
            categories.contains(PrCheckChipKind.BuildFailed) -> PrCheckChipKind.BuildFailed
            categories.contains(PrCheckChipKind.TestsFailed) -> PrCheckChipKind.TestsFailed
            else -> PrCheckChipKind.ChecksFailed
        }
        val count = failed.size
        val label = when {
            count > 1 && kind == PrCheckChipKind.ChecksFailed -> "$count checks failed"
            count > 1 && kind == PrCheckChipKind.BuildFailed -> "Build failed"
            count > 1 && kind == PrCheckChipKind.TestsFailed -> "Tests failed"
            else -> kind.label
        }
        return PrCheckDisplay(kind, count, label)
    }
}

data class PrCheckDisplay(
    val kind: PrCheckChipKind,
    val failedCount: Int,
    val label: String = kind.label,
) {
    fun toSnap(headSha: String, prUrl: String): PrCheckSnap = PrCheckSnap(
        headSha = headSha,
        prUrl = prUrl,
        kind = kind.name,
        failedCount = failedCount,
        label = label,
    )
}

fun prChecksOpenUrl(prUrl: String): String {
    val trimmed = prUrl.trim().removeSuffix("/")
    return if (trimmed.contains("github.com", ignoreCase = true) && !trimmed.endsWith("/checks")) {
        "$trimmed/checks"
    } else {
        trimmed
    }
}
