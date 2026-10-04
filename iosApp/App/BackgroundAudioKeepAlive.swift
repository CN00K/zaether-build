import AVFoundation

/// Process keep-alive for background agent turns on iOS < 26.
///
/// `BGContinuedProcessingTask` requires iOS 26; on earlier releases the only
/// reliable way to keep the process (and the in-process Alpine runtime) alive
/// for long-running work is an active audio session. This coordinator plays a
/// silent looping buffer while an agent lease is held and stops when the last
/// lease ends, so the system neither suspends the app nor kills the runtime.
final class BackgroundAudioKeepAlive {
    static let shared = BackgroundAudioKeepAlive()

    private var player: AVAudioPlayer?
    private var sessionActivated = false

    private init() {}

    func start() {
        if player != nil { return }
        do {
            if !sessionActivated {
                try AVAudioSession.sharedInstance().setCategory(.playback, options: [.mixWithOthers])
                try AVAudioSession.sharedInstance().setActive(true)
                sessionActivated = true
            }
            let player = try AVAudioPlayer(data: Self.silentLoopWav(), fileTypeHint: AVFileType.wav.rawValue)
            player.numberOfLoops = -1
            player.volume = 0.0001
            player.prepareToPlay()
            player.play()
            self.player = player
        } catch {
            NSLog("Aether background keep-alive failed to start: %@", error.localizedDescription)
        }
    }

    func stop() {
        player?.stop()
        player = nil
        guard sessionActivated else { return }
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
        } catch {
            NSLog("Aether background keep-alive failed to deactivate: %@", error.localizedDescription)
        }
        sessionActivated = false
    }

    /// A single silent 16-bit mono frame padded to one second. Generated at
    /// runtime so no bundled asset is needed; the buffer loops indefinitely.
    private static func silentLoopWav() -> Data {
        let sampleRate: Int = 8_000
        let seconds: Int = 1
        let frameCount = sampleRate * seconds
        let dataSize = frameCount * 2
        var data = Data()
        func append(_ value: UInt32) {
            var le = value.littleEndian
            data.append(Data(bytes: &le, count: 4))
        }
        func append16(_ value: UInt16) {
            var le = value.littleEndian
            data.append(Data(bytes: &le, count: 2))
        }
        data.append(contentsOf: Array("RIFF".utf8))
        append(UInt32(36 + dataSize))
        data.append(contentsOf: Array("WAVE".utf8))
        data.append(contentsOf: Array("fmt ".utf8))
        append(16)
        append16(1)              // PCM
        append16(1)              // mono
        append(UInt32(sampleRate))
        append(UInt32(sampleRate * 2)) // byte rate
        append16(2)              // block align
        append16(16)             // bits per sample
        data.append(contentsOf: Array("data".utf8))
        append(UInt32(dataSize))
        data.append(contentsOf: [UInt8](repeating: 0, count: dataSize))
        return data
    }
}
