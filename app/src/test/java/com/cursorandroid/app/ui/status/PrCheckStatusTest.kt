package com.cursorandroid.app.ui.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrCheckStatusTest {
    @Test
    fun classifierMapsBuildAndTestNames() {
        assertEquals(PrCheckChipKind.BuildFailed, PrCheckClassifier.category("preview-apk"))
        assertEquals(PrCheckChipKind.BuildFailed, PrCheckClassifier.category("assembleDebug"))
        assertEquals(PrCheckChipKind.TestsFailed, PrCheckClassifier.category("unit-tests"))
        assertEquals(PrCheckChipKind.ChecksFailed, PrCheckClassifier.category("codeql"))
    }

    @Test
    fun summarizeBuildFailureChip() {
        val runs = listOf(
            PrCheckRunView("preview-apk", "completed", "failure"),
            PrCheckRunView("lint", "completed", "success"),
        )
        val display = PrCheckSummary.summarize(runs)
        assertEquals(PrCheckChipKind.BuildFailed, display?.kind)
        assertEquals("Build failed", display?.label)
    }

    @Test
    fun summarizeMultipleGenericFailures() {
        val runs = listOf(
            PrCheckRunView("codeql", "completed", "failure"),
            PrCheckRunView("security", "completed", "failure"),
        )
        val display = PrCheckSummary.summarize(runs)
        assertEquals(PrCheckChipKind.ChecksFailed, display?.kind)
        assertEquals("2 checks failed", display?.label)
    }

    @Test
    fun summarizePendingChecksRunning() {
        val runs = listOf(
            PrCheckRunView("preview-apk", "in_progress", null),
        )
        val display = PrCheckSummary.summarize(runs)
        assertEquals(PrCheckChipKind.ChecksRunning, display?.kind)
    }

    @Test
    fun summarizeAllGreenIsNull() {
        val runs = listOf(
            PrCheckRunView("preview-apk", "completed", "success"),
        )
        assertNull(PrCheckSummary.summarize(runs))
    }

    @Test
    fun runningAgentWithFailedChecksMapsSeparately() {
        assertEquals(RunIndicator.Running, runIndicator("ACTIVE", "ERROR"))
        assertEquals(RunIndicator.Running, runIndicator("RUNNING", "ERROR"))
        val runs = listOf(PrCheckRunView("preview-apk", "completed", "failure"))
        val chip = PrCheckSummary.summarize(runs)!!.toSnap("abc123", "https://github.com/acme/app/pull/1")
        assertEquals("Build failed", chip.label)
        assertEquals(PrCheckChipKind.BuildFailed, chip.chipKind)
    }

    @Test
    fun checksUrlAppendsChecksPath() {
        assertEquals(
            "https://github.com/acme/app/pull/4/checks",
            prChecksOpenUrl("https://github.com/acme/app/pull/4"),
        )
    }
}
