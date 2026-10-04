package com.zhousl.aether.data

import com.zhousl.aether.data.pi.SharedHostToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.Serializable

/**
 * Workspace checkpoints: a git-backed snapshot taken before every agent turn
 * so the user can undo agent file changes with one tap (Cline checkpoints /
 * Aider auto-commit). Implemented entirely inside the Alpine runtime via the
 * host bash tool — no new native surface required.
 */
class SharedWorkspaceCheckpointManager(
    private val bash: suspend (String) -> SharedHostToolResult,
) {
    companion object {
        /** Identifier for the checkpoint currently being previewed in the UI. */
        const val MaxCheckpoints = 30

        internal fun checkpointCommitMessage(turnId: String): String =
            "aether-checkpoint $turnId"
    }

    @Serializable
    data class WorkspaceCheckpoint(
        val turnId: String,
        val commitHash: String,
        val createdAtMillis: Long,
        val filesChanged: Int,
        val summary: String,
    )

    private val checkpoints = mutableListOf<WorkspaceCheckpoint>()
    private var initialized = false

    suspend fun ensureRepository(workspaceRoot: String) {
        if (initialized) return
        bash("command -v git >/dev/null 2>&1 || apk add --no-cache git >/dev/null 2>&1; " +
            "cd '${escape(workspaceRoot)}' && " +
            "if [ ! -d .aether-checkpoints ]; then git init -q --separate-git-dir=.aether-checkpoints-git .aether-checkpoints 2>/dev/null; fi; " +
            "cd .aether-checkpoints 2>/dev/null && git config user.email aether@local && git config user.name aether; " +
            "cd ..")
        initialized = true
    }

    /**
     * Snapshots the workspace before an agent turn. Copies workspace files into
     * the checkpoint worktree and commits them tagged with the turn id.
     */
    suspend fun createCheckpoint(
        workspaceRoot: String,
        turnId: String,
        nowMillis: Long,
    ): WorkspaceCheckpoint? {
        ensureRepository(workspaceRoot)
        val root = escape(workspaceRoot)
        val result = bash(
            "cd $root && " +
                "rm -rf .aether-checkpoints/worktree && mkdir -p .aether-checkpoints/worktree && " +
                "find . -maxdepth 1 ! -name '.' ! -name '.aether-checkpoints' ! -name '.aether-checkpoints-git' " +
                "-exec cp -R {} .aether-checkpoints/worktree/ \\; 2>/dev/null; " +
                "cd .aether-checkpoints && " +
                "git add -A >/dev/null 2>&1 && " +
                "git commit -q -m '${escape(checkpointCommitMessage(turnId))}' >/dev/null 2>&1; " +
                "git log -1 --format='%H %ci' 2>/dev/null; " +
                "git show --stat --format='' HEAD 2>/dev/null | tail -1"
        )
        if (result.isError) return null
        val stdout = result.hostToolStdout().trim()
        val lines = stdout.lineSequence().filter(String::isNotBlank).toList()
        val hash = lines.getOrNull(0)?.substringBefore(' ')?.takeIf { it.length >= 7 } ?: return null
        val stat = lines.getOrNull(1).orEmpty()
        val checkpoint = WorkspaceCheckpoint(
            turnId = turnId,
            commitHash = hash,
            createdAtMillis = nowMillis,
            filesChanged = Regex("(\\d+) files? changed").find(stat)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            summary = stat.ifBlank { "Snapshot" },
        )
        checkpoints.add(0, checkpoint)
        while (checkpoints.size > MaxCheckpoints) checkpoints.removeAt(checkpoints.size - 1)
        return checkpoint
    }

    /**
     * Restores the workspace to a checkpoint: copies the snapshot files back
     * over the workspace (leaving unrelated runtime state untouched).
     */
    suspend fun restore(workspaceRoot: String, checkpoint: WorkspaceCheckpoint): Boolean {
        val root = escape(workspaceRoot)
        val result = bash(
            "cd $root/.aether-checkpoints && " +
                "git checkout -q '${escape(checkpoint.commitHash)}' 2>/dev/null && " +
                "cd $root && " +
                "cp -R .aether-checkpoints/worktree/. . 2>/dev/null; " +
                "rm -rf .aether-checkpoints/worktree && mkdir -p .aether-checkpoints/worktree && " +
                "find . -maxdepth 1 ! -name '.' ! -name '.aether-checkpoints' ! -name '.aether-checkpoints-git' " +
                "-exec cp -R {} .aether-checkpoints/worktree/ \\; 2>/dev/null; " +
                "cd .aether-checkpoints && git add -A >/dev/null 2>&1 && " +
                "git commit -q -m '${escape(checkpointCommitMessage(checkpoint.turnId + "-restore"))}' >/dev/null 2>&1; " +
                "echo restored"
        )
        return !result.isError && result.hostToolStdout().contains("restored")
    }

    fun list(): List<WorkspaceCheckpoint> = checkpoints.toList()

    private fun escape(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

private fun SharedHostToolResult.hostToolStdout(): String =
    hostToolPayload(outputJson)["stdout"]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun hostToolPayload(outputJson: String): JsonObject =
    Json.parseToJsonElement(outputJson).jsonObject
