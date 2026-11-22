package com.sukisu.ultra.ui.component.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 危险操作（删除 / 卸载 / 清空 / 重置）的确认按钮：红字，不用主色。
 *
 * 收在一个地方是为了统一配色——此前每个弹窗各自写一遍 colorScheme.error，
 * 想调色得全仓找；漏写的地方就还是主色，看起来「删除」和「确定」一样安全。
 */
@Composable
internal fun DestructiveTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
    )
}
