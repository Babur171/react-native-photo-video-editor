import AVFoundation
import UIKit

/// Owns the AVPlayer preview instance, the ordered `VideoClip` list (the
/// multi-clip timeline, Milestone 7), and the `VideoTransformState` that
/// applies globally across the whole composed sequence. The source is never
/// modified — `VideoExporter` reads the clip list again to produce the
/// exported file.
///
/// Unlike Android (which plays a multi-item ExoPlayer playlist and has to
/// track a separate "global" position distinct from the player's own
/// per-item position), the preview here concatenates every clip's trimmed
/// range into a single `AVMutableComposition` and plays that one asset — so
/// `player.currentTime()`/`player.seek(to:)` already ARE the composed-timeline
/// position, with no extra bookkeeping needed. This is a deliberate,
/// architecture-appropriate difference from the Android session, not an
/// oversight.
final class VideoEditSession {
  let player = AVPlayer()
  /// Text/sticker/shape overlays. Reuses the photo layer model/stack — see `PhotoLayer.startMs`/`endMs` for timing.
  let layerStack = PhotoLayerStack()
  private(set) var clips: [VideoClip] = []
  private(set) var state = VideoTransformState()
  /// Fires once the source is probed and the initial single-clip timeline is ready.
  var onClipsReady: (() -> Void)?

  private(set) var naturalSize: CGSize = .zero
  private var cachedAsset: AVURLAsset?
  private var statusObservation: NSKeyValueObservation?
  private let sourceUri: String

  init(sourceUri: String) {
    self.sourceUri = sourceUri
    let resolvedPath = SourceResolver.resolvePath(sourceUri: sourceUri, tempPrefix: "pve_video_preview")
    let url = resolvedPath.map { URL(fileURLWithPath: $0) }
      ?? (URL(string: sourceUri)?.scheme != nil ? URL(string: sourceUri)! : URL(fileURLWithPath: sourceUri))
    let asset = AVURLAsset(url: url)
    cachedAsset = asset
    let probeItem = AVPlayerItem(asset: asset)
    statusObservation = probeItem.observe(\.status, options: [.initial, .new]) { [weak self] observedItem, _ in
      guard let self, observedItem.status == .readyToPlay else { return }
      DispatchQueue.main.async { [weak self] in
        guard let self, self.clips.isEmpty else { return }
        if let track = asset.tracks(withMediaType: .video).first {
          let size = track.naturalSize.applying(track.preferredTransform)
          self.naturalSize = CGSize(width: max(1, abs(size.width)), height: max(1, abs(size.height)))
        }
        let durationSeconds = CMTimeGetSeconds(asset.duration)
        guard durationSeconds.isFinite, durationSeconds > 0 else { return }
        self.clips = [VideoClip(sourceUri: sourceUri, originalDurationMs: Int64(durationSeconds * 1000))]
        self.rebuildPlayerComposition()
        self.onClipsReady?()
      }
    }
    player.replaceCurrentItem(with: probeItem)
  }

  /// Sum of every clip's trimmed duration — the length of the composed timeline.
  var durationMs: Int64 { clips.reduce(0) { $0 + $1.trimmedDurationMs() } }

  func update(_ transform: (inout VideoTransformState) -> Void) {
    transform(&state)
  }

  /// Applies `transform` to the clip list and rebuilds the preview composition to match,
  /// preserving the playhead's position on the composed timeline as closely as possible.
  func updateClips(_ transform: ([VideoClip]) -> [VideoClip]) {
    let previousPositionMs = Int64(CMTimeGetSeconds(player.currentTime()) * 1000)
    clips = transform(clips)
    rebuildPlayerComposition()
    player.seek(to: CMTime(value: previousPositionMs.clamped(to: 0...max(0, durationMs)), timescale: 1000))
  }

  /// The `AVURLAsset` for one clip's own source file (all clips share one source this milestone).
  func asset(for clip: VideoClip) -> AVURLAsset {
    if let cached = cachedAsset, clip.sourceUri == sourceUri { return cached }
    let resolvedPath = SourceResolver.resolvePath(sourceUri: clip.sourceUri, tempPrefix: "pve_video_preview")
    let url = resolvedPath.map { URL(fileURLWithPath: $0) }
      ?? (URL(string: clip.sourceUri)?.scheme != nil ? URL(string: clip.sourceUri)! : URL(fileURLWithPath: clip.sourceUri))
    let asset = AVURLAsset(url: url)
    if clip.sourceUri == sourceUri { cachedAsset = asset }
    return asset
  }

  private func rebuildPlayerComposition() {
    guard !clips.isEmpty else { return }
    let composition = AVMutableComposition()
    let videoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
    let audioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
    var cursor = CMTime.zero
    for clip in clips {
      let clipAsset = asset(for: clip)
      let range = CMTimeRange(
        start: CMTime(value: clip.trimStartMs, timescale: 1000),
        end: CMTime(value: clip.effectiveTrimEndMs(), timescale: 1000)
      )
      if let sourceVideoTrack = clipAsset.tracks(withMediaType: .video).first {
        try? videoTrack?.insertTimeRange(range, of: sourceVideoTrack, at: cursor)
        videoTrack?.preferredTransform = sourceVideoTrack.preferredTransform
      }
      if let sourceAudioTrack = clipAsset.tracks(withMediaType: .audio).first {
        try? audioTrack?.insertTimeRange(range, of: sourceAudioTrack, at: cursor)
      }
      cursor = CMTimeAdd(cursor, range.duration)
    }
    let item = AVPlayerItem(asset: composition)
    player.replaceCurrentItem(with: item)
  }

  func release() {
    statusObservation?.invalidate()
    player.pause()
  }
}

private extension Int64 {
  func clamped(to range: ClosedRange<Int64>) -> Int64 { Swift.min(Swift.max(self, range.lowerBound), range.upperBound) }
}
