package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.cursorandroid.app.AppContainer

@Composable
fun GithubTokenField(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    var githubToken by remember { mutableStateOf(container.store.githubToken.orEmpty()) }
    OutlinedTextField(
        value = githubToken,
        onValueChange = {
            githubToken = it
            container.store.githubToken = it
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text("GitHub token") },
        placeholder = { Text("Personal access token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
}
