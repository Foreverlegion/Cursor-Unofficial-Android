package com.cursorandroid.app.ui.status

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class PrCheckChipKind {
    ChecksRunning,
    BuildFailed,
    TestsFailed,
    ChecksFailed,
    ;

    val categoryPrefix: String
        get() = when (this) {
            BuildFailed -> "Build failed"
            TestsFailed -> "Test failed"
            ChecksFailed -> "Check failed"
            ChecksRunning -> "Checks running"
        }
}

@Serializable
data class PrCheckFailureItem(
    @SerialName("kind") val kind: String = "",
    @SerialName("checkName") val checkName: String = "",
    @SerialName("detail") val detail: String = "",
    @SerialName("label") val label: String = "",
    @SerialName("url") val url: String = "",
) {
    val chipKind: PrCheckChipKind?
        get() = kind.takeIf { it.isNotBlank() }?.let { runCatching { PrCheckChipKind.valueOf(it) }.getOrNull() }
}

@Serializable
data class PrCheckSnap(
    @SerialName("headSha") val headSha: String = "",
    @SerialName("prUrl") val prUrl: String = "",
    @SerialName("kind") val kind: String = "",
    @SerialName("failedCount") val failedCount: Int = 0,
    @SerialName("label") val label: String = "",
    @SerialName("failures") val failures: List<PrCheckFailureItem> = emptyList(),
) {
    val chipKind: PrCheckChipKind?
        get() = kind.takeIf { it.isNotBlank() }?.let { runCatching { PrCheckChipKind.valueOf(it) }.getOrNull() }

    val showChip: Boolean
        get() = chipKind != null && (label.isNotBlank() || failures.isNotEmpty())
}

data class PrCheckRunView(
    val name: String,
    val status: String,
    val conclusion: String?,
    val id: Long = 0L,
    val htmlUrl: String = "",
    val outputText: String? = null,
    val testDetail: String? = null,
)

object PrCheckClassifier {
    private val BUILD = Regex("""(?i)(build|assemble|apk|preview-apk|gradle|packag|compile)""")
    private val TEST = Regex("""(?i)(test|unit|instrument|robolectric|jest|pytest|mocha)""")

    fun category(name: String): PrCheckChipKind {
        val n = name.trim()
        if (TEST.containsMatchIn(n)) return PrCheckChipKind.TestsFailed
        if (BUILD.containsMatchIn(n)) return PrCheckChipKind.BuildFailed
        return PrCheckChipKind.ChecksFailed
    }
}

object PrCheckNaming {
    fun failureLabel(kind: PrCheckChipKind, checkName: String, testDetail: String? = null): String {
        val job = checkName.trim().ifBlank { "check" }
        val subject = when {
            kind == PrCheckChipKind.TestsFailed && !testDetail.isNullOrBlank() -> testDetail.trim()
            else -> job
        }
        return "${kind.categoryPrefix}: $subject"
    }

    fun chipLabel(failures: List<PrCheckFailureItem>): String {
        if (failures.isEmpty()) return ""
        val first = failures.first().label.ifBlank {
            failures.first().chipKind?.let { kind ->
                failureLabel(kind, failures.first().checkName, failures.first().detail.takeIf { it.isNotBlank() })
            }.orEmpty()
        }
        val extra = failures.size - 1
        return if (extra > 0) "$first +$extra more" else first
    }
}

object PrCheckTestDetail {
    private val dottedTest = Regex("""([A-Z][\w$]*Test[\w$]*\.[\w$]+)""")
    private val simpleMethod = Regex("""([A-Z][\w$]*)\.([\w$]+)""")

    fun fromAnnotations(titles: List<String>, messages: List<String> = emptyList()): String? {
        (titles + messages).forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            dottedTest.find(line)?.groupValues?.get(1)?.let { return it }
            if (line.contains('.') && line.length <= 96 && !line.contains(' ')) return line
        }
        return null
    }

    fun fromOutput(text: String?): String? {
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) return null
        dottedTest.find(body)?.groupValues?.get(1)?.let { return it }
        body.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.contains("FAILED") || trimmed.contains("failed")) {
                simpleMethod.find(trimmed)?.let { match ->
                    val candidate = "${match.groupValues[1]}.${match.groupValues[2]}"
                    if (candidate.contains("Test") || match.groupValues[1].endsWith("Test")) return candidate
                }
            }
        }
        return null
    }

    fun merge(checkName: String, outputText: String?, annotationTitles: List<String>, annotationMessages: List<String>): String? {
        return fromAnnotations(annotationTitles, annotationMessages)
            ?: fromOutput(outputText)
            ?: fromOutput(checkName)
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
            return if (pending) {
                PrCheckDisplay(PrCheckChipKind.ChecksRunning, failedCount = 0, label = PrCheckChipKind.ChecksRunning.categoryPrefix)
            } else {
                null
            }
        }
        val items = failed
            .sortedBy { it.name.lowercase() }
            .map { run ->
                val kind = PrCheckClassifier.category(run.name)
                val detail = run.testDetail?.trim().orEmpty()
                val label = PrCheckNaming.failureLabel(kind, run.name, detail.takeIf { it.isNotEmpty() })
                PrCheckFailureItem(
                    kind = kind.name,
                    checkName = run.name,
                    detail = detail,
                    label = label,
                    url = run.htmlUrl,
                )
            }
        val kind = items.first().chipKind ?: PrCheckChipKind.ChecksFailed
        return PrCheckDisplay(
            kind = kind,
            failedCount = items.size,
            label = PrCheckNaming.chipLabel(items),
            failures = items,
        )
    }
}

data class PrCheckDisplay(
    val kind: PrCheckChipKind,
    val failedCount: Int,
    val label: String = kind.categoryPrefix,
    val failures: List<PrCheckFailureItem> = emptyList(),
) {
    fun toSnap(headSha: String, prUrl: String): PrCheckSnap = PrCheckSnap(
        headSha = headSha,
        prUrl = prUrl,
        kind = kind.name,
        failedCount = failedCount,
        label = label,
        failures = failures,
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

fun prCheckItemUrl(item: PrCheckFailureItem, prUrl: String): String {
    val direct = item.url.trim()
    if (direct.isNotEmpty()) return direct
    return prChecksOpenUrl(prUrl)
}
