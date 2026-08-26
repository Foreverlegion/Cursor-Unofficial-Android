package com.cursorandroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cursorandroid.app.data.notify.RunNotifier
import com.cursorandroid.app.ui.CursorApp

class MainActivity : ComponentActivity() {
    private var launch by mutableStateOf(LaunchRequest())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launch = LaunchRequest.from(intent, System.currentTimeMillis())
        val app = application as CursorAndroidApp
        consumeNotice(intent, app)
        setContent {
            CursorApp(
                container = app.container,
                launch = launch,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launch = LaunchRequest.from(intent, System.currentTimeMillis())
        consumeNotice(intent, application as CursorAndroidApp)
    }

    private fun consumeNotice(intent: Intent?, app: CursorAndroidApp) {
        val id = intent?.getStringExtra(RunNotifier.EXTRA_NOTICE_ID)?.takeIf { it.isNotBlank() } ?: return
        app.container.notices.dismiss(id)
        app.container.notifier.rememberDismissed(id)
    }

}
