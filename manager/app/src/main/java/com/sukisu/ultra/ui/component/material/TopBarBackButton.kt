package com.sukisu.ultra.ui.component.material

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.ui.theme.LocalTopBarStyle

@Composable
fun TopBarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    // A floating bar already paints one glass circle behind this button. Keeping the filled
    // container would nest a second circle inside it, and the horizontal padding would stretch
    // that outer circle into a capsule.
    val floating = LocalTopBarStyle.current.isFloating
    IconButton(
        modifier = if (floating) modifier else modifier.padding(horizontal = 10.dp),
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (floating) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
        )
    }
}
