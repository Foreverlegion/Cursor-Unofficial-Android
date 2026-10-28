package com.cursorandroid.app.data.repo

import com.cursorandroid.app.ui.inbox.HideFinishedAge
import org.junit.Assert.assertEquals
import org.junit.Test

class HideFinishedPrefsTest {
    @Test
    fun defaultIsOff() {
        assertEquals(0, DEFAULT_HIDE_FINISHED_DAYS)
        assertEquals(HideFinishedAge.Off, HideFinishedAge.fromDays(0))
    }

    @Test
    fun offAndOtherChoicesPersist() {
        assertEquals(0, normalizeHideFinishedDays(0))
        assertEquals(7, normalizeHideFinishedDays(7))
        assertEquals(84, normalizeHideFinishedDays(84))
    }

    @Test
    fun unknownValuesFallBackToOff() {
        assertEquals(0, normalizeHideFinishedDays(99))
    }

    @Test
    fun legacyThreeWeeksMapsToFourWeeks() {
        assertEquals(28, normalizeHideFinishedDays(21))
    }

    @Test
    fun fromDaysMapsOffExplicitly() {
        assertEquals(HideFinishedAge.Off, HideFinishedAge.fromDays(0))
    }
}
