package com.sukisu.ultra.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 聊天输入框上方的快捷控制条（Material3 外壳）。
 *
 * 三件事都直接改配置，不跳设置页：换模型、调推理强度、开关联网搜索。
 * 推理强度与联网搜索在「生效中」时图标用 primary 色，关闭时回落成普通颜色。
 */
@Composable
internal fun AiComposerQuickBarMaterial(
    config: AiConfig,
    search: AiSearchConfig,
    onSelectModel: (String) -> Unit,
    onSetThinkingMode: (AiThinkingMode) -> Unit,
    onSetSearchEnabled: (Boolean) -> Unit,
    onOpenSettings: (AiSettingsTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showModels by remember { mutableStateOf(false) }
    var showThinking by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    val models = config.quickModels()
    val modelLabel = config.quickModelLabel()
    val modes = config.quickThinkingModes()
    val thinking = config.quickThinkingMode()
    val thinkingOn = thinking != AiThinkingMode.OFF
    val searchOn = search.searchEnabled

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { showModels = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = modelLabel.ifBlank { stringResource(R.string.ai_model_not_selected) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.ai_quick_models_title),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(2.dp))
        IconButton(onClick = { showThinking = true }) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = stringResource(R.string.ai_quick_thinking_title),
                modifier = Modifier.size(20.dp),
                tint = if (thinkingOn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = { showSearch = true }) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = stringResource(R.string.ai_quick_search_title),
                modifier = Modifier.size(20.dp),
                tint = if (searchOn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }

    if (showModels) {
        AlertDialog(
            onDismissRequest = { showModels = false },
            title = { Text(stringResource(R.string.ai_quick_models_title)) },
            text = {
                if (models.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_quick_no_models),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    ) {
                        models.forEach { entry ->
                            val selected = entry.label == modelLabel || entry.id == modelLabel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectModel(entry.id)
                                        showModels = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModels = false }) {
                    Text(stringResource(R.string.ai_close))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showModels = false
                    onOpenSettings(AiSettingsTarget.Model)
                }) {
                    Text(stringResource(R.string.ai_settings))
                }
            },
        )
    }

    if (showThinking) {
        // 滑块按「支持档位」的序号走：0 是自动，最后一档是该模型允许的最高强度。
        val maxIndex = (modes.size - 1).toFloat()
        var sliderValue by remember(modes.size, thinking) {
            mutableFloatStateOf(modes.indexOf(thinking).coerceAtLeast(0).toFloat())
        }
        val currentMode = modes.getOrElse(sliderValue.roundToInt()) { modes.first() }
        // 档位变化才震一下：拖动过程里每一帧都反馈会变成持续的嗡嗡声。
        val haptic = LocalHapticFeedback.current
        var lastIndex by remember { mutableIntStateOf(sliderValue.roundToInt()) }
        AlertDialog(
            onDismissRequest = { showThinking = false },
            title = { Text(stringResource(R.string.ai_quick_thinking_title)) },
            text = {
                Column {
                    // 档位名居中：和下方滑杆当前点对齐；底部留 16dp，把字和滑杆分开。
                    Text(
                        text = stringResource(currentMode.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                    if (maxIndex > 0f) {
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = it
                                val index = it.roundToInt()
                                if (index != lastIndex) {
                                    lastIndex = index
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                modes.getOrNull(index)?.let(onSetThinkingMode)
                            },
                            valueRange = 0f..maxIndex,
                            steps = (modes.size - 2).coerceAtLeast(0),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(modes.first().labelRes()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(modes.last().labelRes()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.ai_quick_thinking_single),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThinking = false }) {
                    Text(stringResource(R.string.ai_done))
                }
            },
        )
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text(stringResource(R.string.ai_quick_search_title)) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.ai_search_enable),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        AiExpressiveSwitch(checked = searchOn, onCheckedChange = onSetSearchEnabled)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    search.quickSearchProviders().forEach { state ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = providerTitle(state.provider),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = when {
                                    !state.configured -> stringResource(R.string.ai_search_missing_key)
                                    state.enabled -> stringResource(R.string.ai_quick_provider_on)
                                    else -> stringResource(R.string.ai_quick_provider_off)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.enabled && state.configured) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearch = false }) {
                    Text(stringResource(R.string.ai_done))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSearch = false
                    onOpenSettings(AiSettingsTarget.Search)
                }) {
                    Text(stringResource(R.string.ai_settings))
                }
            },
        )
    }
}
