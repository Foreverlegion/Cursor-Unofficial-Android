package com.cursorandroid.app.data.repo

import com.cursorandroid.app.ui.inbox.HideFinishedAge
import org.junit.Assert.assertEquals
import org.junit.Test

class HideFinishedPrefsTest {
    @Test
    fun defaultIsThreeWeeks() {
        assertEquals(21, DEFAULT_HIDE_FINISHED_DAYS)
        assertEquals(HideFinishedAge.Week3, HideFinishedAge.fromDays(21))
    }

    @Test
    fun offAndOtherChoicesPersist() {
        assertEquals(0, normalizeHideFinishedDays(0))
        assertEquals(7, normalizeHideFinishedDays(7))
    }

    @Test
    fun unknownValuesFallBackToDefault() {
        assertEquals(21, normalizeHideFinishedDays(99))
    }

    @Test
    fun fromDaysMapsOffExplicitly() {
        assertEquals(HideFinishedAge.Off, HideFinishedAge.fromDays(0))
    }
}
