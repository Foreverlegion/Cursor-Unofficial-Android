package com.cursorandroid.app.ui.chat

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.repo.ModelPickerStore
import com.cursorandroid.app.ui.theme.CursorTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ModelPickerAnchorFieldTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var store: ModelPickerStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("cursor_model_picker", Context.MODE_PRIVATE).edit().clear().commit()
        store = ModelPickerStore(context)
    }

    @Test
    fun tapFieldOpensPickerSheet() {
        var open by mutableStateOf(false)
        val models = listOf(ModelItem(id = "composer-2.5", displayName = "Composer 2.5"))
        compose.setContent {
            CursorTheme {
                Column {
                    ModelPickerAnchorField(
                        value = "Account default",
                        label = "Model",
                        expanded = open,
                        onExpandedChange = { open = it },
                    )
                    ModelPickerSheet(
                        open = open,
                        title = "Model",
                        models = models,
                        selectedModelId = "",
                        store = store,
                        onDismiss = { open = false },
                        onPick = { open = false },
                    )
                }
            }
        }
        compose.onNodeWithText("Account default").performClick()
        compose.onNodeWithText("Search models").assertIsDisplayed()
    }
}
