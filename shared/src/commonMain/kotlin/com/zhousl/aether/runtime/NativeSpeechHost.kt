package com.zhousl.aether.runtime

interface NativeSpeechListener {
    fun onPartialText(text: String)
    fun onFinalText(text: String)
    fun onError(message: String)
}

interface NativeSpeechHost {
    /** Starts dictation; partial results stream through the listener. */
    fun startDictation(listener: NativeSpeechListener): Boolean
    fun stopDictation()
    fun isDictationAvailable(listener: NativeBooleanResultListener)
    /** Speaks text aloud; call again to stop. */
    fun speak(text: String, listener: NativeUnitResultListener): Boolean
    fun stopSpeaking()
    fun isSpeaking(listener: NativeBooleanResultListener)
}
