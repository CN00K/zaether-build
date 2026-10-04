package com.zhousl.aether.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private val hapticGenerator = UIImpactFeedbackGenerator(
    style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight,
)

actual fun platformHapticFeedback() {
    hapticGenerator.impactOccurred()
}
