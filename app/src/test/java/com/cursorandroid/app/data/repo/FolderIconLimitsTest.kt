package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderIconLimitsTest {
    @Test
    fun rejectsOversizeFiles() {
        val msg = FolderIconLimits.inputRejectMessage(
            bytes = FolderIconLimits.MAX_INPUT_BYTES + 1,
            width = 1000,
            height = 1000,
        )
        assertTrue(msg!!.contains("50 MB"))
    }

    @Test
    fun rejectsHundredMegapixelImages() {
        val msg = FolderIconLimits.inputRejectMessage(
            bytes = null,
            width = 10_000,
            height = 10_001,
        )
        assertTrue(msg!!.contains("100 megapixel"))
    }

    @Test
    fun acceptsTypicalPhonePhotoBounds() {
        assertNull(
            FolderIconLimits.inputRejectMessage(
                bytes = 8_000_000,
                width = 4032,
                height = 3024,
            ),
        )
    }

    @Test
    fun inSampleSizeStepsDownHugeDimensions() {
        assertEquals(1, FolderIconLimits.computeInSampleSize(256, 256))
        assertEquals(8, FolderIconLimits.computeInSampleSize(16_384, 12_288))
    }
}
