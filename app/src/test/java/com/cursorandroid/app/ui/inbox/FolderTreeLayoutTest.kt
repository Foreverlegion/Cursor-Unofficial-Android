package com.cursorandroid.app.ui.inbox

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTreeLayoutTest {

    @Test
    fun startPaddingGrowsByStepPerDepth() {
        assertEquals(16, folderTreeStartPadding(0).value.toInt())
        assertEquals(34, folderTreeStartPadding(1).value.toInt())
        assertEquals(52, folderTreeStartPadding(2).value.toInt())
    }

    @Test
    fun childDepthIncrementsRecursively() {
        assertEquals(1, folderTreeChildDepth(0))
        assertEquals(2, folderTreeChildDepth(folderTreeChildDepth(0)))
        assertEquals(3, folderTreeChildDepth(folderTreeChildDepth(folderTreeChildDepth(0))))
    }

    @Test
    fun agentsInsideExpandedGroupSitOneLevelDeeper() {
        assertEquals(2, folderTreeNestedGroupAgentDepth(1))
        assertEquals(3, folderTreeNestedGroupAgentDepth(2))
    }

    @Test
    fun a11yMentionsParentFolderWhenNested() {
        assertEquals("stocks", folderTreeRowA11y(0, "stocky", "stocks"))
        assertEquals("stocks, in folder stocky", folderTreeRowA11y(1, "stocky", "stocks"))
        assertEquals("Fix login, in folder stocky", folderTreeRowA11y(2, "stocky", "Fix login"))
    }

    @Test
    fun folderGuideContinuesOnNestedGroupChats() {
        assertEquals(emptyList<Int>(), folderTreeExtraGuideDepths(1, "stocky"))
        assertEquals(listOf(1), folderTreeExtraGuideDepths(2, "stocky"))
        assertEquals(listOf(1, 2), folderTreeGuideDepths(2, "stocky"))
        assertEquals(listOf(1), folderTreeGuideDepths(1, "stocky"))
        assertEquals(listOf(2), folderTreeGuideDepths(2, null))
    }
}
