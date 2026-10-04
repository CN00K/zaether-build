import AVFoundation
import Speech

/// Dictation (SFSpeechRecognizer) and TTS (AVSpeechSynthesizer) for Aether.
/// Voice input inserts into the composer; TTS reads responses aloud.
final class AetherSpeechHost: NSObject, AVSpeechSynthesizerDelegate {
    static let shared = AetherSpeechHost()

    private var recognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    private var dictationListener: NativeSpeechListener?
    private var partialDebounce: DispatchWorkItem?

    private let synthesizer = AVSpeechSynthesizer()

    func isDictationAvailable(listener: NativeBooleanResultListener) {
        let status = SFSpeechRecognizer.authorizationStatus()
        let micPermission = AVAudioSession.sharedInstance().recordPermission
        let available = SFSpeechRecognizer.supportsOnDeviceRecognition || true
        listener.onResult(available && status != .denied && micPermission != .denied)
    }

    func startDictation(listener: NativeSpeechListener) -> Bool {
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            guard status == .authorized else {
                listener.onError(message: "Speech recognition permission denied.")
                return
            }
            AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
                DispatchQueue.main.async {
                    granted ? self?.beginDictation(listener: listener)
                        : listener.onError(message: "Microphone permission denied.")
                }
            }
        }
        return true
    }

    private func beginDictation(listener: NativeSpeechListener) {
        stopDictation()
        dictationListener = listener
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let locale = Locale.current
            recognizer = SFSpeechRecognizer(locale: locale) ?? SFSpeechRecognizer()
            guard let recognizer, recognizer.isAvailable else {
                listener.onError(message: "Speech recognition unavailable for this locale.")
                return
            }

            recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
            recognitionRequest?.shouldReportPartialResults = true
            if #available(iOS 13, *), recognizer.supportsOnDeviceRecognition {
                recognitionRequest?.requiresOnDeviceRecognition = false
            }

            let inputNode = audioEngine.inputNode
            let format = inputNode.outputFormat(forBus: 0)
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                self?.recognitionRequest?.append(buffer)
            }
            audioEngine.prepare()
            try audioEngine.start()

            recognitionTask = recognizer.recognitionTask(with: recognitionRequest!) { [weak self] result, error in
                guard let self else { return }
                if let result {
                    let text = result.bestTranscription.formattedString
                    if result.isFinal {
                        listener.onFinalText(text: text)
                        self.stopDictation()
                    } else {
                        self.partialDebounce?.cancel()
                        let work = DispatchWorkItem { listener.onPartialText(text: text) }
                        self.partialDebounce = work
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2, execute: work)
                    }
                }
                if error != nil {
                    listener.onError(message: "Dictation failed.")
                    self.stopDictation()
                }
            }
        } catch {
            listener.onError(message: error.localizedDescription)
            stopDictation()
        }
    }

    func stopDictation() {
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        recognitionTask?.cancel()
        recognitionTask = nil
        partialDebounce?.cancel()
        partialDebounce = nil
        dictationListener = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func speak(text: String, listener: NativeUnitResultListener) -> Bool {
        // Strip markdown noise before speaking.
        let cleaned = text
            .replacingOccurrences(of: "```[\\s\\S]*?```", with: " code block ", options: .regularExpression)
            .replacingOccurrences(of: "[*_#>`~\\[\\]()]", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return false }
        stopSpeaking()
        let utterance = AVSpeechUtterance(string: String(cleaned.prefix(4000)))
        utterance.voice = AVSpeechSynthesisVoice(language: Locale.current.identifier)
            ?? AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.delegate = self
        synthesizer.speak(utterance)
        return true
    }

    func stopSpeaking() {
        synthesizer.stopSpeaking(at: .immediate)
    }

    func isSpeaking(listener: NativeBooleanResultListener) {
        listener.onResult(synthesizer.isSpeaking)
    }
}
