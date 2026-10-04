package com.zhousl.aether.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

data class PlatformAccessibilityPreferences(
    val reduceMotion: Boolean = false,
    val increasedContrast: Boolean = false,
)

val LocalReduceMotion = compositionLocalOf { false }

@Composable
expect fun rememberPlatformAccessibilityPreferences(): PlatformAccessibilityPreferences

/**
 * Fires a short mechanical haptic tick (iOS: UIImpactFeedbackGenerator light).
 * No-op on platforms without native haptics.
 */
expect fun platformHapticFeedback()
