package com.cursorandroid.app.data.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotifyPrefsMigrationTest {
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("notify_prefs_migration_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun neverTouchedLiveToggleBecomesOffAfterMigration() {
        migrateNotifyLiveDefaults(prefs)
        assertFalse(prefs.getBoolean(LIVE_RUN_STATUS_KEY, true))
    }

    @Test
    fun explicitLiveOnPersistsThroughMigration() {
        prefs.edit().putBoolean(LIVE_RUN_STATUS_KEY, true).commit()
        migrateNotifyLiveDefaults(prefs)
        assertTrue(prefs.getBoolean(LIVE_RUN_STATUS_KEY, false))
    }

    @Test
    fun explicitLiveOffPersistsThroughMigration() {
        prefs.edit().putBoolean(LIVE_RUN_STATUS_KEY, false).commit()
        migrateNotifyLiveDefaults(prefs)
        assertFalse(prefs.getBoolean(LIVE_RUN_STATUS_KEY, true))
    }

    @Test
    fun migrationRunsOnce() {
        migrateNotifyLiveDefaults(prefs)
        prefs.edit().putBoolean(LIVE_RUN_STATUS_KEY, true).commit()
        migrateNotifyLiveDefaults(prefs)
        assertTrue(prefs.getBoolean(LIVE_RUN_STATUS_KEY, false))
    }
}
