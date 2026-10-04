package com.zhousl.aether.data

/**
 * Switchable agent modes (Roo Code style): each mode swaps the system prompt
 * addendum and restricts which host tools the agent may use. Modes are purely
 * a prompt/tooling layer — the underlying runtime is unchanged.
 */
enum class SharedAgentMode(
    val id: String,
    val label: String,
    val promptAddendum: String,
    val disabledTools: Set<String>,
) {
    Agent(
        id = "agent",
        label = "Agent",
        promptAddendum = "",
        disabledTools = emptySet(),
    ),
    Ask(
        id = "ask",
        label = "Ask",
        promptAddendum = "Answer questions without modifying anything. " +
            "Read-only mode: do not write, edit, or create files, and do not run " +
            "commands that change state unless explicitly asked.",
        disabledTools = setOf("write", "edit"),
    ),
    Architect(
        id = "architect",
        label = "Architect",
        promptAddendum = "Plan and design first. Produce specifications, " +
            "tradeoffs, and step-by-step plans. Do not write or edit files; " +
            "describe what should change instead.",
        disabledTools = setOf("write", "edit"),
    ),
    Debug(
        id = "debug",
        label = "Debug",
        promptAddendum = "Diagnose problems methodically: reproduce, isolate, " +
            "explain the root cause, then propose the minimal fix. Prefer " +
            "reading and running diagnostics over broad edits.",
        disabledTools = emptySet(),
    );

    companion object {
        val Default = Agent

        fun fromId(id: String): SharedAgentMode =
            entries.firstOrNull { it.id == id } ?: Default

        fun toolFilter(mode: SharedAgentMode, toolName: String): Boolean =
            toolName !in mode.disabledTools
    }
}
