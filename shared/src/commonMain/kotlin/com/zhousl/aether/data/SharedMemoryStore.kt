package com.zhousl.aether.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cross-session persistent memory (open-webui style): the agent records
 * durable facts about the user as short statements; all remembered facts are
 * injected into every conversation's system prompt. Stored as a JSON array in
 * the runtime workspace, shared across sessions and restarts.
 */
@Serializable
data class SharedMemoryFact(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
)

object SharedMemoryStore {
    private val json = Json { ignoreUnknownKeys = true }
    const val MaxFacts = 100
    const val MaxFactLength = 400

    fun decode(raw: String): List<SharedMemoryFact> = runCatching {
        json.decodeFromString<List<SharedMemoryFact>>(raw)
            .filter { it.text.isNotBlank() }
            .take(MaxFacts)
            .map { it.copy(text = it.text.take(MaxFactLength)) }
    }.getOrDefault(emptyList())

    fun encode(facts: List<SharedMemoryFact>): String =
        json.encodeToString(
            facts.filter { it.text.isNotBlank() }
                .take(MaxFacts)
                .map { it.copy(text = it.text.take(MaxFactLength)) },
        )

    fun buildPromptBlock(facts: List<SharedMemoryFact>): String? {
        if (facts.isEmpty()) return null
        return "Persistent memory about the user (from previous conversations):\n" +
            facts.joinToString("\n") { "- ${it.text}" } +
            "\nUse this context naturally; do not announce that you remember things."
    }
}
