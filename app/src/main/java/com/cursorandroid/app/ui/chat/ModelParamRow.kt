package com.cursorandroid.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.namedVariants
import com.cursorandroid.app.data.api.setParam

@Composable
fun ModelParamRow(
    model: ModelItem?,
    params: List<ModelParam>,
    onParams: (List<ModelParam>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (model == null) return
    val variants = model.namedVariants()
    if (variants.size > 1) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(variants, key = { it.displayName + it.params.joinToString { p -> "${p.id}=${p.value}" } }) { variant ->
                FilterChip(
                    selected = variant.params == params,
                    onClick = { onParams(variant.params) },
                    label = { Text(variant.displayName ?: model.id) },
                )
            }
        }
        return
    }
    model.parameters.orEmpty().forEach { parameter ->
        if (parameter.values.size <= 1) return@forEach
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            parameter.values.forEach { option ->
                FilterChip(
                    selected = params.firstOrNull { it.id == parameter.id }?.value == option.value,
                    onClick = { onParams(model.setParam(params, parameter.id, option.value)) },
                    label = { Text(option.displayName ?: option.value) },
                )
            }
        }
    }
}
