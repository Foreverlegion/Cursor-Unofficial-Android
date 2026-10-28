package com.cursorandroid.app.ui.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrCheckStatusTest {
    @Test
    fun classifierMapsBuildTestAndGenericChecks() {
        assertEquals(PrCheckChipKind.BuildFailed, PrCheckClassifier.category("preview-apk"))
        assertEquals(PrCheckChipKind.BuildFailed, PrCheckClassifier.category("assembleDebug"))
        assertEquals(PrCheckChipKind.TestsFailed, PrCheckClassifier.category("unit-tests"))
        assertEquals(PrCheckChipKind.ChecksFailed, PrCheckClassifier.category("codeql"))
        assertEquals(PrCheckChipKind.ChecksFailed, PrCheckClassifier.category("lint"))
    }

    @Test
    fun failureLabelUsesJobOrTestName() {
        assertEquals(
            "Build failed: Assemble preview APK",
            PrCheckNaming.failureLabel(PrCheckChipKind.BuildFailed, "Assemble preview APK"),
        )
        assertEquals(
            "Test failed: unit-tests",
            PrCheckNaming.failureLabel(PrCheckChipKind.TestsFailed, "unit-tests"),
        )
        assertEquals(
            "Test failed: AgentListLogicTest.ageFilter",
            PrCheckNaming.failureLabel(
                PrCheckChipKind.TestsFailed,
                "unit-tests",
                "AgentListLogicTest.ageFilter",
            ),
        )
        assertEquals(
            "Check failed: lint",
            PrCheckNaming.failureLabel(PrCheckChipKind.ChecksFailed, "lint"),
        )
    }

    @Test
    fun chipLabelShowsFirstFailurePlusMore() {
        val failures = listOf(
            PrCheckFailureItem(
                kind = PrCheckChipKind.BuildFailed.name,
                checkName = "Assemble preview APK",
                label = "Build failed: Assemble preview APK",
            ),
            PrCheckFailureItem(
                kind = PrCheckChipKind.ChecksFailed.name,
                checkName = "lint",
                label = "Check failed: lint",
            ),
        )
        assertEquals("Build failed: Assemble preview APK +1 more", PrCheckNaming.chipLabel(failures))
    }

    @Test
    fun summarizeBuildFailureUsesJobName() {
        val runs = listOf(
            PrCheckRunView("Assemble preview APK", "completed", "failure"),
            PrCheckRunView("lint", "completed", "success"),
        )
        val display = PrCheckSummary.summarize(runs)
        assertEquals(PrCheckChipKind.BuildFailed, display?.kind)
        assertEquals("Build failed: Assemble preview APK", display?.label)
        assertEquals(1, display?.failures?.size)
    }

    @Test
    fun summarizeMultipleFailuresChipShowsFirstPlusMore() {
        val runs = listOf(
            PrCheckRunView("codeql", "completed", "failure"),
            PrCheckRunView("security", "completed", "failure"),
        )
        val display = PrCheckSummary.summarize(runs)
        assertEquals(PrCheckChipKind.ChecksFailed, display?.kind)
        assertEquals("Check failed: codeql +1 more", display?.label)
        assertEquals(2, display?.failures?.size)
    }

    @Test
    fun summarizeTestFailureUsesAnnotationDetail() {
        val runs = listOf(
            PrCheckRunView(
                name = "unit-tests",
                status = "completed",
                conclusion = "failure",
                testDetail = "AgentListLogicTest.ageFilter",
            ),
        )
        val display = PrCheckSummary.summarize(runs)
        assertEquals("Test failed: AgentListLogicTest.ageFilter", display?.label)
    }

    @Test
    fun summarizePendingChecksRunning() {
        val runs = listOf(
            PrCheckRunView("preview-apk", "in_progress", null),
        )
        val display = PrCheckSummary.summarize(runs)
        assertEquals(PrCheckChipKind.ChecksRunning, display?.kind)
        assertEquals("Checks running", display?.label)
    }

    @Test
    fun summarizeAllGreenIsNull() {
        val runs = listOf(
            PrCheckRunView("preview-apk", "completed", "success"),
        )
        assertNull(PrCheckSummary.summarize(runs))
    }

    @Test
    fun testDetailFromOutputAndAnnotations() {
        assertEquals(
            "AgentListLogicTest.ageFilter",
            PrCheckTestDetail.fromAnnotations(listOf("AgentListLogicTest.ageFilter")),
        )
        assertEquals(
            "AgentListLogicTest.ageFilter",
            PrCheckTestDetail.fromOutput("FAILURE: AgentListLogicTest.ageFilter expected 1 was 0"),
        )
    }

    @Test
    fun runningAgentWithFailedChecksMapsSeparately() {
        assertEquals(RunIndicator.Running, runIndicator("ACTIVE", "ERROR"))
        val runs = listOf(PrCheckRunView("Assemble preview APK", "completed", "failure"))
        val chip = PrCheckSummary.summarize(runs)!!.toSnap("abc123", "https://github.com/acme/app/pull/1")
        assertEquals("Build failed: Assemble preview APK", chip.label)
        assertEquals(PrCheckChipKind.BuildFailed, chip.chipKind)
    }

    @Test
    fun checksUrlAppendsChecksPath() {
        assertEquals(
            "https://github.com/acme/app/pull/4/checks",
            prChecksOpenUrl("https://github.com/acme/app/pull/4"),
        )
    }

    @Test
    fun itemUrlFallsBackToCheckRunLink() {
        val item = PrCheckFailureItem(url = "https://github.com/acme/app/runs/1", label = "x")
        assertEquals("https://github.com/acme/app/runs/1", prCheckItemUrl(item, "https://github.com/acme/app/pull/4"))
    }
}
