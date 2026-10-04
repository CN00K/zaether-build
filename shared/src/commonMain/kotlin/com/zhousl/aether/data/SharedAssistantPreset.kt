package com.zhousl.aether.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Assistant presets (Cherry Studio style): a system prompt + agent mode +
 * optional prompt shortcuts packaged as one JSON document. Import via the
 * composer plus-menu or a picked file; export from the settings screen.
 */
@Serializable
data class SharedAssistantPreset(
    val name: String,
    val systemPrompt: String = "",
    val agentModeId: String = "",
    val promptShortcuts: List<SharedPromptShortcut> = emptyList(),
    val version: Int = 1,
)

object SharedAssistantPresetStore {
    private val json = Json { ignoreUnknownKeys = true }
    private const val MaxNameLength = 80
    private const val MaxPromptLength = 8_000

    fun decode(raw: String): SharedAssistantPreset? = runCatching {
        val preset = json.decodeFromString<SharedAssistantPreset>(raw)
        require(preset.name.isNotBlank()) { "Preset name is required." }
        preset.copy(
            name = preset.name.take(MaxNameLength),
            systemPrompt = preset.systemPrompt.take(MaxPromptLength),
        )
    }.getOrNull()

    fun encode(preset: SharedAssistantPreset): String =
        json.encodeToString(preset)

    /** Bundled curated presets, available without importing anything. */
    fun builtinPresets(): List<SharedAssistantPreset> = listOf(
        SharedAssistantPreset(
            name = "Default Assistant",
            systemPrompt = "",
            agentModeId = "agent",
        ),
        SharedAssistantPreset(
            name = "Coding Partner",
            systemPrompt = "You are a pragmatic coding partner. Prefer minimal, " +
                "correct changes; explain tradeoffs briefly; never invent APIs.",
            agentModeId = "debug",
            promptShortcuts = listOf(
                SharedPromptShortcut("pc-explain", "解释", "逐段解释上面的代码。"),
                SharedPromptShortcut("pc-test", "写测试", "为上面的代码写单元测试。"),
            ),
        ),
        SharedAssistantPreset(
            name = "Writer",
            systemPrompt = "You are a precise writing assistant. Preserve the " +
                "author's voice; never pad; cut ruthlessly.",
            agentModeId = "ask",
            promptShortcuts = listOf(
                SharedPromptShortcut("pw-polish", "润色", "润色上面的文字，保持原意。"),
                SharedPromptShortcut("pw-shorten", "缩写", "把上面的文字压缩到一半长度。"),
            ),
        ),
        SharedAssistantPreset(
            name = "Architect",
            systemPrompt = "You are a software architect. Produce specifications " +
                "with tradeoffs, constraints, and step-by-step plans.",
            agentModeId = "architect",
        ),
    )
}
