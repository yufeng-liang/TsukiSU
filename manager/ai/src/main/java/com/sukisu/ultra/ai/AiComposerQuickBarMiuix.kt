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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.roundToInt

/**
 * 聊天输入框上方的快捷控制条（Miuix 外壳）。
 *
 * 与 Material3 版同一套数据与回调，只是换成 Miuix 的弹窗与控件；
 * 推理强度、联网搜索「生效中」时图标用 primary 色。
 */
@Composable
internal fun AiComposerQuickBarMiuix(
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
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.ai_quick_models_title),
                modifier = Modifier.size(16.dp),
                tint = colorScheme.onSurfaceVariantActions,
            )
        }
        Spacer(Modifier.size(2.dp))
        IconButton(onClick = { showThinking = true }) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = stringResource(R.string.ai_quick_thinking_title),
                modifier = Modifier.size(20.dp),
                tint = if (thinkingOn) colorScheme.primary else colorScheme.onSurfaceVariantActions,
            )
        }
        IconButton(onClick = { showSearch = true }) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = stringResource(R.string.ai_quick_search_title),
                modifier = Modifier.size(20.dp),
                tint = if (searchOn) colorScheme.primary else colorScheme.onSurfaceVariantActions,
            )
        }
    }

    if (showModels) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.ai_quick_models_title),
            onDismissRequest = { showModels = false },
            content = {
                if (models.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_quick_no_models),
                        style = MiuixTheme.textStyles.body2,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
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
                                    style = MiuixTheme.textStyles.body1,
                                    color = if (selected) colorScheme.primary else colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.ai_settings),
                        onClick = {
                            showModels = false
                            onOpenSettings(AiSettingsTarget.Model)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.ai_close),
                        onClick = { showModels = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }

    if (showThinking) {
        val maxIndex = (modes.size - 1).toFloat()
        var sliderValue by remember(modes.size, thinking) {
            mutableFloatStateOf(modes.indexOf(thinking).coerceAtLeast(0).toFloat())
        }
        val currentMode = modes.getOrElse(sliderValue.roundToInt()) { modes.first() }
        OverlayDialog(
            show = true,
            title = stringResource(R.string.ai_quick_thinking_title),
            onDismissRequest = { showThinking = false },
            content = {
                // 档位名居中对齐，正好落在下方滑杆当前点附近；底部留 16dp，把大字和滑杆分开。
                Text(
                    text = stringResource(currentMode.labelRes()),
                    style = MiuixTheme.textStyles.title4,
                    color = colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    textAlign = TextAlign.Center,
                )
                if (maxIndex > 0f) {
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            modes.getOrNull(it.roundToInt())?.let(onSetThinkingMode)
                        },
                        valueRange = 0f..maxIndex,
                        // 每个档位画一个刻度点，滑到档位时手上有一下反馈，不用盯着屏幕看数字。
                        steps = (modes.size - 2).coerceAtLeast(0),
                        showKeyPoints = true,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(modes.first().labelRes()),
                            style = MiuixTheme.textStyles.body2,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = stringResource(modes.last().labelRes()),
                            style = MiuixTheme.textStyles.body2,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.ai_quick_thinking_single),
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.ai_done),
                        onClick = { showThinking = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }

    if (showSearch) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.ai_quick_search_title),
            onDismissRequest = { showSearch = false },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ai_search_enable),
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = searchOn, onCheckedChange = onSetSearchEnabled)
                }
                search.quickSearchProviders().forEach { state ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = providerTitle(state.provider),
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when {
                                !state.configured -> stringResource(R.string.ai_search_missing_key)
                                state.enabled -> stringResource(R.string.ai_quick_provider_on)
                                else -> stringResource(R.string.ai_quick_provider_off)
                            },
                            style = MiuixTheme.textStyles.body2,
                            color = if (state.enabled && state.configured) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariantSummary
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.ai_settings),
                        onClick = {
                            showSearch = false
                            onOpenSettings(AiSettingsTarget.Search)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.ai_done),
                        onClick = { showSearch = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }
}
