package com.cursorandroid.app.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkBarTest {
    @Test
    fun workBarNamesTheLiveTool() {
        assertEquals(
            "Agent working · read_file",
            workActivityLine(false, "RUNNING", "RUNNING", "cloud", "read_file"),
        )
        assertEquals(
            "Agent working · starting",
            workActivityLine(false, "CREATING", null, "cloud", null),
        )
    }
}
