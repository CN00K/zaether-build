package com.zhousl.aether.ui

/**
 * Delay between markdown re-parses while streaming. Chunks arriving faster
 * than this collapse into the next parse; the final content always parses.
 */
internal const val SharedMarkdownParseThrottleMillis = 120L
