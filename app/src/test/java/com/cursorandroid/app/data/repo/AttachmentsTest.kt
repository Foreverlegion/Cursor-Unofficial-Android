package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.PromptImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentsTest {
    @Test
    fun normalizeMimeMapsJpegAliases() {
        assertEquals("image/jpeg", Attachments.normalizeMime("image/jpg"))
        assertEquals("image/jpeg", Attachments.normalizeMime("image/pjpeg"))
        assertEquals("image/jpeg", Attachments.normalizeMime("IMAGE/JPEG"))
        assertEquals("image/png", Attachments.normalizeMime("image/png"))
    }

    @Test
    fun officialMimesMatchCloudAgentsApi() {
        assertTrue(Attachments.isOfficialImageMime("image/jpeg"))
        assertTrue(Attachments.isOfficialImageMime("image/jpg"))
        assertTrue(Attachments.isOfficialImageMime("image/png"))
        assertTrue(Attachments.isOfficialImageMime("image/gif"))
        assertTrue(Attachments.isOfficialImageMime("image/webp"))
        assertFalse(Attachments.isOfficialImageMime("image/heic"))
        assertFalse(Attachments.isOfficialImageMime("image/svg+xml"))
    }

    @Test
    fun promptKeepsImagesAndDefaultCaption() {
        val image = PromptImage(data = "Zm9v", mimeType = "image/jpeg")
        val item = AttachItem(
            id = "1",
            name = "card.jpg",
            mime = "image/jpeg",
            image = image,
        )
        val blank = Attachments.prompt("", listOf(item))
        assertEquals("See attached.", blank.text)
        assertEquals(1, blank.images?.size)
        assertEquals("image/jpeg", blank.images?.first()?.mimeType)

        val named = Attachments.prompt("die card", listOf(item))
        assertEquals("die card", named.text)
        assertNotNull(named.images)
    }

    @Test
    fun promptOmitsImagesWhenNoneReady() {
        val broken = AttachItem(
            id = "1",
            name = "card.jpg",
            mime = "image/jpeg",
            error = "Draft file missing",
        )
        val prompt = Attachments.prompt("See attached.", listOf(broken))
        assertEquals("See attached.", prompt.text)
        assertNull(prompt.images)
    }
}
