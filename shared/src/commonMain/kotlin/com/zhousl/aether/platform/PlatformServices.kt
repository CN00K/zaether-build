package com.zhousl.aether.platform

/** Streaming dictation handle: partial text updates, final on close. */
class PlatformDictationSession {
    var partialText: String = ""
    var finalText: String = ""
    var isFinal: Boolean = false
    var error: String = ""
}

data class PlatformPickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class PlatformPickedDirectoryFile(
    val relativePath: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class PlatformPickedDirectory(
    val name: String,
    val files: List<PlatformPickedDirectoryFile>,
)

interface PlatformServices {
    suspend fun pickFile(imagesOnly: Boolean = false, maximumBytes: Long = DefaultPickedFileBytes): PlatformPickedFile?
    suspend fun pickFiles(imagesOnly: Boolean = false): List<PlatformPickedFile> =
        listOfNotNull(pickFile(imagesOnly))
    suspend fun pickDirectory(): PlatformPickedDirectory? = null
    /** Returns null when the user cancels the platform file picker. */
    suspend fun exportFile(name: String, mimeType: String, bytes: ByteArray): Boolean? = false
    fun copyText(text: String): Boolean
    /** Dictation: null result means unavailable/permission denied. */
    suspend fun startDictation(): PlatformDictationSession? = null
    fun stopDictation() {}
    /** Text-to-speech; returns false when unsupported. */
    fun speak(text: String): Boolean = false
    fun stopSpeaking() {}
    fun shareText(title: String, text: String): Boolean
    fun shareFile(name: String, mimeType: String, bytes: ByteArray): Boolean = false
    fun previewFile(name: String, mimeType: String, bytes: ByteArray): Boolean = false
    fun openAlpineFileManager(): Boolean = false
    fun openUrl(url: String): Boolean
    /** Opens OAuth UI and reports a loopback/custom-scheme callback when the platform can intercept it. */
    fun openAuthenticationUrl(
        url: String,
        onCallback: (String) -> Unit = {},
        onCancelled: () -> Unit = {},
    ): Boolean = openUrl(url)
    fun terminateApplication(): Boolean = false

    companion object {
        /** Default cap for single-file picks (attachments, imports). */
        const val DefaultPickedFileBytes: Long = 16L * 1024L * 1024L
        /** Skill archives may legitimately be larger than attachment picks. */
        const val MaxPickedSkillArchiveBytes: Long = 32L * 1024L * 1024L
    }
}

object NoOpPlatformServices : PlatformServices {
    override suspend fun pickFile(imagesOnly: Boolean, maximumBytes: Long): PlatformPickedFile? = null
    override fun copyText(text: String): Boolean = false
    override fun shareText(title: String, text: String): Boolean = false
    override fun openUrl(url: String): Boolean = false
}
