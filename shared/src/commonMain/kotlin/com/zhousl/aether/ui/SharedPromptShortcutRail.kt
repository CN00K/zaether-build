package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.SharedPromptShortcut
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import com.zhousl.aether.ui.theme.AetherSurfaceHigh

/**
 * Horizontal rail of user-defined quick prompt shortcuts above the composer.
 * Hidden while the user is editing a message or typing a slash command.
 */
@Composable
fun SharedPromptShortcutRail(
    shortcuts: List<SharedPromptShortcut>,
    onShortcut: (SharedPromptShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shortcuts.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(shortcuts, key = SharedPromptShortcut::id) { shortcut ->
            SharedPromptShortcutChip(shortcut = shortcut, onShortcut = onShortcut)
        }
    }
}

@Composable
private fun SharedPromptShortcutChip(
    shortcut: SharedPromptShortcut,
    onShortcut: (SharedPromptShortcut) -> Unit,
) {
    Box(
        modifier = Modifier
            .background(AetherSurfaceHigh, RoundedCornerShape(16.dp))
            .clickable { onShortcut(shortcut) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = shortcut.label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
