package com.zhousl.aether.platform

actual fun platformHapticFeedback() {
    // No-op on Android: Compose's clickable modifier already provides
    // platform-standard touch feedback (ripple + system haptics).
}
