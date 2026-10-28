package com.cursorandroid.app.data.repo

const val DEFAULT_HIDE_FINISHED_DAYS = 21

private val VALID_HIDE_FINISHED_DAYS = setOf(0, 1, 3, 7, 21)

fun normalizeHideFinishedDays(days: Int): Int =
    if (days in VALID_HIDE_FINISHED_DAYS) days else DEFAULT_HIDE_FINISHED_DAYS
