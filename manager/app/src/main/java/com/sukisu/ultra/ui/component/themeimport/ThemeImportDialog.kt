package com.sukisu.ultra.ui.component.themeimport

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.screen.settings.ThemePackageOptions
import com.sukisu.ultra.ui.util.ImportedTheme

@Composable
fun ThemeImportDialog(
    show: Boolean,
    theme: ImportedTheme?,
    onDismissRequest: () -> Unit,
    onConfirm: (ThemePackageOptions) -> Unit,
) {
    if (!show || theme == null) return

    val data = theme.data
    ThemeOptionsDialog(
        show = show,
        title = stringResource(R.string.theme_import_dialog_title),
        message = stringResource(R.string.theme_import_dialog_message),
        hasBackgroundImage = theme.backgroundPath != null,
        hasCardImage = theme.cardBackgroundPath != null,
        hasStickers = theme.stickerPaths.isNotEmpty(),
        hasAppIcon = data.appIconId != null,
        hasCustomTexts = !data.customTexts.isNullOrEmpty() || !data.customHomeTitle.isNullOrBlank(),
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}
