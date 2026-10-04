package com.zhousl.aether.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * User-defined quick prompt shortcuts (NextChat-style chips): saved reusable
 * prompts shown as a horizontal chip rail above the composer. Tapping a chip
 * inserts its prompt into the input; sendDirectly chips submit immediately.
 */
@Serializable
data class SharedPromptShortcut(
    val id: String,
    val label: String,
    val prompt: String,
    val sendDirectly: Boolean = false,
)

object SharedPromptShortcutStore {
    private val json = Json { ignoreUnknownKeys = true }
    private const val MaxShortcuts = 24
    private const val MaxPromptLength = 4_000

    fun defaults(): List<SharedPromptShortcut> = listOf(
        SharedPromptShortcut("sc-translate", "翻译", "把上一条回复翻译成中文，保留代码和术语原文。"),
        SharedPromptShortcut("sc-summarize", "总结", "用要点列表总结本次对话的关键内容。"),
        SharedPromptShortcut("sc-continue", "继续", "从上次中断的地方继续。"),
        SharedPromptShortcut("sc-explain", "解释代码", "逐段解释上面代码的作用，标注关键逻辑。"),
        SharedPromptShortcut("sc-review", "审查", "审查上面的内容，指出问题和改进建议。"),
    )

    fun decode(raw: String): List<SharedPromptShortcut> = runCatching {
        val decoded = json.decodeFromString<List<SharedPromptShortcut>>(raw)
        decoded.filter { it.label.isNotBlank() && it.prompt.isNotBlank() }
            .take(MaxShortcuts)
            .map { it.copy(prompt = it.prompt.take(MaxPromptLength)) }
    }.getOrDefault(defaults())

    fun encode(shortcuts: List<SharedPromptShortcut>): String =
        json.encodeToString(
            shortcuts.filter { it.label.isNotBlank() && it.prompt.isNotBlank() }
                .take(MaxShortcuts)
                .map { it.copy(prompt = it.prompt.take(MaxPromptLength)) },
        )
}
