package com.cursorandroid.app.data.notify

import android.content.SharedPreferences
import androidx.core.content.edit

/** Runs once per install when upgrading to live-run grouping defaults (1.0.35). */
internal fun migrateNotifyLiveDefaults(prefs: SharedPreferences) {
    if (prefs.getBoolean(NOTIFY_LIVE_DEFAULTS_MIGRATED, false)) return
    prefs.edit {
        if (!contains(LIVE_RUN_STATUS_KEY)) {
            putBoolean(LIVE_RUN_STATUS_KEY, false)
        }
        putBoolean(NOTIFY_LIVE_DEFAULTS_MIGRATED, true)
    }
}

internal const val LIVE_RUN_STATUS_KEY = "live_run_status"
internal const val MAX_LIVE_PROMOTED_KEY = "max_live_promoted"
private const val NOTIFY_LIVE_DEFAULTS_MIGRATED = "notify_live_defaults_migrated_v135"
