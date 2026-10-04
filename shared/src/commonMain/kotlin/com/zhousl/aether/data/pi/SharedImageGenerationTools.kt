package com.zhousl.aether.data.pi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Image generation host tool: shells out to curl against the active provider's
 * images API (OpenAI-compatible POST /images/generations) through the runtime's
 * bash tool, then returns the image as base64 for inline markdown rendering.
 * No new HTTP client dependencies; works with any OpenAI-compatible provider.
 */
class SharedImageGenerationTools(
    private val bash: suspend (String) -> SharedHostToolResult,
    private val activeModelId: () -> String,
    private val activeBaseUrl: () -> String,
    private val activeApiKey: () -> String,
) : SharedHostToolExecutor {
    companion object {
        const val ToolName = "image_generate"
    }

    override val definitions: JsonArray = JsonArray(emptyList())

    override suspend fun execute(name: String, arguments: JsonObject): SharedHostToolResult = try {
        when (name) {
            ToolName -> generate(arguments)
            else -> toolError("Unknown tool '$name'.", errorKey = "error")
        }
    } catch (cancellationException: kotlinx.coroutines.CancellationException) {
        throw cancellationException
    } catch (error: Throwable) {
        toolError(message = error.message ?: "Image generation failed.", errorKey = "error")
    }

    private suspend fun generate(arguments: JsonObject): SharedHostToolResult {
        val prompt = arguments.toString()
        val model = activeModelId()
        val baseUrl = activeBaseUrl().trimEnd('/')
        val apiKey = activeApiKey()
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return toolError("No provider is configured for image generation.", errorKey = "error")
        }
        val body = buildJsonObject {
            put("model", model)
            // Prompt is embedded as a JSON string via the shell-safe heredoc below.
            put("prompt", "")
            put("size", "1024x1024")
            put("n", 1)
        }.toString()
        // Write the request body through stdin to avoid quoting issues; the
        // prompt is injected as a single-quoted JSON string arg instead.
        val quotedPrompt = "'" + arguments.toString().replace("'", "'\\''") + "'"
        val result = bash(
            "PROMPT=$quotedPrompt; BODY=" + "'" + body.replace("'", "'\\''") + "'; " +
                "printf '%s' \"\$BODY\" | sed 's/\"prompt\":\"\"/\"prompt\":'\"\$PROMPT\"'/' | " +
                "curl -sS --max-time 300 -X POST '$baseUrl/images/generations' " +
                "-H 'Authorization: Bearer '\"\$(printf '%s' '$apiKey' | sed \"s/'/'\\\\\\\\''/g\")\"' " +
                "-H 'Content-Type: application/json' -d @- 2>&1"
        )
        if (result.isError) {
            val stderr = result.jsonField("stderr").ifBlank { result.jsonField("errmsg") }
            return toolError(stderr.ifBlank { "Image request failed." }, errorKey = "error")
        }
        val stdout = result.jsonField("stdout")
        val hasImage = stdout.contains("b64_json")
        return if (hasImage) {
            SharedHostToolResult(
                outputJson = buildJsonObject {
                    put("ok", true)
                    put("stdout", stdout.take(1_000_000))
                    put("image_response", stdout)
                }.toString(),
                isError = false,
            )
        } else {
            toolError("The provider returned no image: " + stdout.take(500), errorKey = "error")
        }
    }
}

private fun SharedHostToolResult.jsonField(key: String): String = try {
    val obj = kotlinx.serialization.json.Json.parseToJsonElement(outputJson) as? JsonObject
    ((obj?.get(key)) as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
} catch (_: Exception) {
    ""
}

private fun SharedImageGenerationTools.toolError(
    message: String,
    errorKey: String = "error",
): SharedHostToolResult = SharedHostToolResult(
    outputJson = buildJsonObject {
        put("ok", false)
        put(errorKey, message)
        put("errmsg", message)
    }.toString(),
    isError = true,
)
