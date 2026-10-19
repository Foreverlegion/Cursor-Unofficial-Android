package com.cursorandroid.app.ui.composeAgent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import com.cursorandroid.app.data.api.MAX_ENV_VARS

@Composable
fun EnvVarFields(
    title: String,
    hint: String,
    rows: List<Pair<String, String>>,
    onRows: (List<Pair<String, String>>) -> Unit,
    enabled: Boolean = true,
    maskValues: Boolean = true,
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    rows.forEachIndexed { index, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = row.first,
                onValueChange = { value ->
                    onRows(rows.toMutableList().also { it[index] = value to row.second })
                },
                modifier = Modifier.weight(1f),
                label = { Text("Name") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            OutlinedTextField(
                value = row.second,
                onValueChange = { value ->
                    onRows(rows.toMutableList().also { it[index] = row.first to value })
                },
                modifier = Modifier.weight(1f),
                label = { Text("Value") },
                singleLine = true,
                enabled = enabled,
                visualTransformation = if (maskValues) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            )
            if (rows.size > 1) {
                IconButton(
                    onClick = { onRows(rows.filterIndexed { i, _ -> i != index }) },
                    enabled = enabled,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove")
                }
            }
        }
    }
    if (rows.size < MAX_ENV_VARS) {
        TextButton(
            onClick = { onRows(rows + ("" to "")) },
            enabled = enabled,
        ) {
            Text("Add")
        }
    }
}
