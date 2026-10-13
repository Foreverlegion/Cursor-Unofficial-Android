package com.cursorandroid.app.ui

import com.cursorandroid.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewPolicyTest {
    @Test fun showsAfterUpdateFromBuildWithoutPopup() =
        assertTrue(WhatsNewPolicy.shouldShow(-1, 129, freshInstall = false, notes = "x"))

    @Test fun showsAfterUpdateFromOlderSeenVersion() =
        assertTrue(WhatsNewPolicy.shouldShow(129, 130, freshInstall = false, notes = "x"))

    @Test fun hiddenOnFreshInstall() =
        assertFalse(WhatsNewPolicy.shouldShow(-1, 129, freshInstall = true, notes = "x"))

    @Test fun hiddenOnceSeen() =
        assertFalse(WhatsNewPolicy.shouldShow(129, 129, freshInstall = false, notes = "x"))

    @Test fun hiddenWithoutNotes() =
        assertFalse(WhatsNewPolicy.shouldShow(-1, 129, freshInstall = false, notes = " "))

    @Test fun bulletsAndBackticks() =
        assertEquals("• a `b`".replace("`", ""), WhatsNewPolicy.displayText("- a `b`"))

    @Test fun buildCarriesCurrentNotes() {
        assertTrue(BuildConfig.WHATS_NEW.isNotBlank())
        assertFalse(BuildConfig.WHATS_NEW.contains("# "))
        assertFalse(BuildConfig.WHATS_NEW.contains("---"))
        assertFalse(BuildConfig.WHATS_NEW.contains("@"))
    }
}
