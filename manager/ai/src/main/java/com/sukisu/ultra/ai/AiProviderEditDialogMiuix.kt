package com.sukisu.ultra.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Miuix 版新增/编辑提供商弹窗。
 *
 * 用途：把「名称 / 接口格式 / 地址 / 密钥」一次填完，明确保存与取消。
 * 单独做成弹窗的原因：卡片里直接堆字段会让人分不清是在改当前提供商还是在新建。
 */
@Composable
internal fun AiProviderEditDialogMiuix(
    provider: AiProvider?,
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (AiProvider) -> Unit,
) {
    var name by remember(provider?.id) { mutableStateOf(provider?.name.orEmpty()) }
    var format by remember(provider?.id) {
        mutableStateOf(provider?.format ?: AiProviderFormat.AUTO)
    }
    var endpoint by remember(provider?.id) { mutableStateOf(provider?.endpoint.orEmpty()) }
    var apiKey by remember(provider?.id) { mutableStateOf(provider?.apiKey.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }
    val endpointValid = endpoint.isBlank() || isHttpEndpoint(endpoint)

    OverlayDialog(
        show = true,
        title = stringResource(
            if (provider == null) R.string.ai_provider_add_title else R.string.ai_provider_edit_title
        ),
        onDismissRequest = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.ai_provider_name) + stringResource(R.string.ai_field_required),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.ai_provider_name_hint, suggestedName),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = stringResource(R.string.ai_format),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                AiProviderFormat.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { item ->
                            MiuixFormatChip(
                                selected = format == item,
                                text = stringResource(item.labelRes()),
                                onClick = { format = item },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                TextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = stringResource(R.string.ai_endpoint) + stringResource(R.string.ai_field_required),
                    useLabelAsPlaceholder = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!endpointValid) {
                    Text(
                        text = stringResource(R.string.ai_endpoint_invalid),
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.error,
                    )
                }
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    // 字符串本身就带「（可选）」：本地推理服务通常不校验密钥。
                    label = stringResource(R.string.ai_api_key),
                    useLabelAsPlaceholder = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = stringResource(
                                    if (showKey) R.string.ai_hide_key else R.string.ai_show_key
                                ),
                                tint = colorScheme.onSurfaceVariantActions,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.ai_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val base = provider ?: AiProvider(
                                id = "p" + System.currentTimeMillis(),
                                name = suggestedName,
                                format = format,
                                endpoint = endpoint,
                                apiKey = apiKey,
                            )
                            onConfirm(
                                base.copy(
                                    name = name.trim().ifBlank { suggestedName },
                                    format = format,
                                    endpoint = endpoint.trim(),
                                    apiKey = apiKey,
                                )
                            )
                        },
                        enabled = endpointValid,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = stringResource(R.string.ai_provider_save),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }
            }
        },
    )
}
