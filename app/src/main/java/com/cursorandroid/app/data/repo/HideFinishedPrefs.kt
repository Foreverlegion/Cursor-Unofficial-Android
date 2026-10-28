package com.cursorandroid.app.data.repo

const val DEFAULT_HIDE_FINISHED_DAYS = 21

val VALID_HIDE_FINISHED_DAYS = setOf(0, 1, 3, 7, 14, 21, 28, 56, 84)

fun normalizeHideFinishedDays(days: Int): Int =
    when {
        days in VALID_HIDE_FINISHED_DAYS -> days
        else -> DEFAULT_HIDE_FINISHED_DAYS
    }
