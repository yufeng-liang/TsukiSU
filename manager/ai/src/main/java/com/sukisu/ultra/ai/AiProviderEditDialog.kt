package com.sukisu.ultra.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** 供应商地址必须是完整的 http(s) URL，保存前即时校验。 */
private fun isHttpEndpoint(value: String): Boolean {
    val uri = runCatching { java.net.URI(value.trim()) }.getOrNull() ?: return false
    return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}

/**
 * 新增/编辑供应商的弹窗。
 *
 * 之前新增供应商是直接在当前页面塞一个“新供应商”，字段散落在卡片里，既没有“正在新增”的反馈，
 * 也容易让人以为是在改当前供应商。改成弹窗后：明确标题、明确保存/取消，字段一次填完。
 */
@Composable
internal fun AiProviderEditDialog(
    provider: AiProvider?,
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (AiProvider) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(provider?.id) { mutableStateOf(provider?.name.orEmpty()) }
    var format by remember(provider?.id) { mutableStateOf(provider?.format ?: AiProviderFormat.AUTO) }
    var endpoint by remember(provider?.id) { mutableStateOf(provider?.endpoint.orEmpty()) }
    var apiKey by remember(provider?.id) { mutableStateOf(provider?.apiKey.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (provider == null) R.string.ai_provider_add_title else R.string.ai_provider_edit_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ai_provider_name)) },
                    placeholder = { Text(suggestedName) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                AiDropdownField(
                    label = stringResource(R.string.ai_format),
                    selectedLabel = stringResource(format.labelRes()),
                    options = AiProviderFormat.entries.map { stringResource(it.labelRes()) },
                    onSelect = { format = AiProviderFormat.entries[it] },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text(stringResource(R.string.ai_endpoint)) },
                    placeholder = { Text(stringResource(R.string.ai_endpoint_hint)) },
                    singleLine = true,
                    isError = endpoint.isNotBlank() && !isHttpEndpoint(endpoint),
                    supportingText = {
                        if (endpoint.isNotBlank() && !isHttpEndpoint(endpoint)) {
                            Text(stringResource(R.string.ai_endpoint_invalid))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.ai_api_key)) },
                    placeholder = { Text(stringResource(R.string.ai_api_key_hint)) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(
                                    if (showKey) R.string.ai_hide_key else R.string.ai_show_key
                                ),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isHttpEndpoint(endpoint),
                onClick = {
                    val finalName = name.trim().ifBlank { suggestedName }
                    onConfirm(
                        (provider ?: AiProvider(
                            id = "p" + System.currentTimeMillis(),
                            name = finalName,
                        )).copy(
                            name = finalName,
                            format = format,
                            endpoint = endpoint.trim(),
                            apiKey = apiKey.trim(),
                        )
                    )
                },
            ) {
                Text(stringResource(R.string.ai_provider_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** 默认名称：按最小未占用序号取「提供商 N」。 */
internal fun nextProviderName(existing: List<AiProvider>, template: String): String {
    val used = existing.map { it.name.trim() }.toSet()
    var index = 1
    while (true) {
        val candidate = String.format(template, index)
        if (candidate !in used) return candidate
        index++
    }
}
