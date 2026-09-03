import AVFoundation
import QuartzCore
import UIKit

struct VideoExportResult {
  let uri: String
  let width: Int
  let height: Int
  let durationMs: Int64
  let fileSize: Int
  let mimeType: String
}

struct VideoExportError: Error {
  let code: String
  let message: String
}

/// Wraps AVFoundation to apply trim/mute/rotate/flip to the full-resolution
/// source video and export an H.264/AAC MP4. The source file is never
/// modified.
///
/// Milestone 7 (multi-clip timeline): each `VideoClip`'s trimmed range is
/// inserted into the same export `AVMutableComposition` back-to-back via
/// `insertTimeRange(_:of:at:)`, mirroring exactly what `VideoEditSession`
/// already does for gapless preview — `AVMutableComposition` supports
/// multi-segment concatenation natively, so no Media3-style multi-item
/// `Composition`/`EditedMediaItemSequence` equivalent is needed here.
///
/// Note: aspect-ratio cropping is Android-only for now. Combining it
/// correctly with the source's own orientation-correcting transform and an
/// additional user rotation in `AVMutableVideoCompositionLayerInstruction`'s
/// coordinate space is easy to get subtly wrong, and there is no device
/// available in this environment to verify the pixel math — so it is scoped
/// out here rather than shipped unverified. Rotate/flip use the well-known,
/// low-risk 90°-increment recipe below instead of general-purpose crop math.
final class VideoExporter {
  private var exportSession: AVAssetExportSession?
  private var progressTimer: Timer?
  private var outputURL: URL?

  func export(
    clips: [VideoClip],
    assetProvider: (VideoClip) -> AVURLAsset,
    state: VideoTransformState,
    layers: [PhotoLayer],
    exportOptions: [String: Any]?,
    onProgress: @escaping (Float) -> Void,
    onComplete: @escaping (VideoExportResult) -> Void,
    onError: @escaping (VideoExportError) -> Void
  ) {
    guard !clips.isEmpty else {
      onError(VideoExportError(code: "E_SOURCE_NOT_FOUND", message: "There are no clips to export."))
      return
    }

    let composition = AVMutableComposition()
    guard let compositionVideoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
      onError(VideoExportError(code: "E_INTERNAL", message: "Unable to build the export composition."))
      return
    }
    let compositionAudioTrack = state.muted ? nil : composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)

    var sourceVideoTrack: AVAssetTrack?
    var cursor = CMTime.zero
    for clip in clips {
      let clipAsset = assetProvider(clip)
      guard let clipVideoTrack = clipAsset.tracks(withMediaType: .video).first else {
        onError(VideoExportError(code: "E_SOURCE_UNREADABLE", message: "A clip's source video could not be decoded."))
        return
      }
      if sourceVideoTrack == nil { sourceVideoTrack = clipVideoTrack }
      let range = CMTimeRange(
        start: CMTime(value: clip.trimStartMs, timescale: 1000),
        end: CMTime(value: clip.effectiveTrimEndMs(), timescale: 1000)
      )
      do {
        try compositionVideoTrack.insertTimeRange(range, of: clipVideoTrack, at: cursor)
      } catch {
        onError(VideoExportError(code: "E_EXPORT_FAILED", message: "Unable to trim the video."))
        return
      }
      if let compositionAudioTrack, let clipAudioTrack = clipAsset.tracks(withMediaType: .audio).first {
        try? compositionAudioTrack.insertTimeRange(range, of: clipAudioTrack, at: cursor)
      }
      cursor = CMTimeAdd(cursor, range.duration)
    }
    let timeRange = CMTimeRange(start: .zero, duration: cursor)
    let durationMs = clips.reduce(Int64(0)) { $0 + $1.trimmedDurationMs() }

    guard let sourceVideoTrack else {
      onError(VideoExportError(code: "E_SOURCE_UNREADABLE", message: "The selected video could not be decoded."))
      return
    }

    let naturalSize = sourceVideoTrack.naturalSize
    let baseTransform = sourceVideoTrack.preferredTransform
    let uprightSize = naturalSize.applying(baseTransform)
    let uprightWidth = abs(uprightSize.width)
    let uprightHeight = abs(uprightSize.height)

    var transform = baseTransform
    if state.flipHorizontal {
      transform = transform.concatenating(CGAffineTransform(scaleX: -1, y: 1))
      transform = transform.concatenating(CGAffineTransform(translationX: uprightWidth, y: 0))
    }

    let normalizedRotation = ((state.rotationDegrees % 360) + 360) % 360
    var renderWidth = uprightWidth
    var renderHeight = uprightHeight
    if normalizedRotation != 0 {
      let radians = CGFloat(normalizedRotation) * .pi / 180
      transform = transform.concatenating(CGAffineTransform(rotationAngle: radians))
      switch normalizedRotation {
      case 90:
        transform = transform.concatenating(CGAffineTransform(translationX: uprightHeight, y: 0))
        renderWidth = uprightHeight
        renderHeight = uprightWidth
      case 180:
        transform = transform.concatenating(CGAffineTransform(translationX: uprightWidth, y: uprightHeight))
      case 270:
        transform = transform.concatenating(CGAffineTransform(translationX: 0, y: uprightWidth))
        renderWidth = uprightHeight
        renderHeight = uprightWidth
      default:
        break
      }
    }

    let videoComposition = AVMutableVideoComposition()
    videoComposition.renderSize = CGSize(width: renderWidth, height: renderHeight)
    videoComposition.frameDuration = CMTime(value: 1, timescale: 30)

    let instruction = AVMutableVideoCompositionInstruction()
    instruction.timeRange = CMTimeRange(start: .zero, duration: timeRange.duration)
    let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: compositionVideoTrack)
    layerInstruction.setTransform(transform, at: .zero)
    instruction.layerInstructions = [layerInstruction]
    videoComposition.instructions = [instruction]

    // KNOWN LIMITATION (iOS-only): every overlay is burned in for the WHOLE clip here, ignoring
    // PhotoLayer.startMs/endMs, even though the editor UI now lets a user set a time range per
    // overlay (see LayerOverlayView-based selection in PhotoVideoEditorViewController). Android
    // respects the range via a per-frame BitmapOverlay callback keyed on presentation time; doing
    // the same on iOS needs a custom AVVideoCompositing implementation, since
    // AVVideoCompositionCoreAnimationTool only composites a fixed CALayer tree, not one that changes
    // per frame. That's a bigger, separate change — see docs/video-editor.md.
    if !layers.isEmpty {
      let renderSize = CGSize(width: renderWidth, height: renderHeight)
      let renderer = PhotoEditSession.pixelRenderer(size: renderSize)
      let transparentBase = renderer.image { _ in }
      let overlayImage = PhotoLayerRenderer.render(transparentBase, layers: layers) { uri in
        SourceResolver.resolvePath(sourceUri: uri, tempPrefix: "pve_video_sticker_export").flatMap { UIImage(contentsOfFile: $0) }
      }
      let videoLayer = CALayer()
      videoLayer.frame = CGRect(origin: .zero, size: renderSize)
      let overlayLayer = CALayer()
      overlayLayer.frame = videoLayer.frame
      overlayLayer.contents = overlayImage.cgImage
      let parentLayer = CALayer()
      parentLayer.frame = videoLayer.frame
      parentLayer.addSublayer(videoLayer)
      parentLayer.addSublayer(overlayLayer)
      videoComposition.animationTool = AVVideoCompositionCoreAnimationTool(postProcessingAsVideoLayer: videoLayer, in: parentLayer)
    }

    let outputDir = FileManager.default.temporaryDirectory.appendingPathComponent("photovideoeditor", isDirectory: true)
    try? FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
    let output = outputDir.appendingPathComponent(UUID().uuidString + ".mp4")
    outputURL = output

    guard let session = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else {
      onError(VideoExportError(code: "E_INTERNAL", message: "Unable to create the export session."))
      return
    }
    session.outputURL = output
    session.outputFileType = .mp4
    session.videoComposition = videoComposition
    exportSession = session

    let timer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: true) { [weak session] _ in
      guard let session else { return }
      onProgress(session.progress)
    }
    progressTimer = timer

    session.exportAsynchronously { [weak self] in
      guard let self else { return }
      self.progressTimer?.invalidate()
      switch session.status {
      case .completed:
        let attributes = try? FileManager.default.attributesOfItem(atPath: output.path)
        let fileSize = (attributes?[.size] as? Int) ?? 0
        onComplete(
          VideoExportResult(
            uri: output.absoluteString,
            width: Int(renderWidth),
            height: Int(renderHeight),
            durationMs: durationMs,
            fileSize: fileSize,
            mimeType: "video/mp4"
          )
        )
      case .cancelled:
        try? FileManager.default.removeItem(at: output)
        onError(VideoExportError(code: "E_EXPORT_CANCELLED", message: "Export was cancelled."))
      default:
        try? FileManager.default.removeItem(at: output)
        onError(VideoExportError(code: "E_EXPORT_FAILED", message: session.error?.localizedDescription ?? "Unable to export the video."))
      }
    }
  }

  func cancel() {
    exportSession?.cancelExport()
    progressTimer?.invalidate()
    if let outputURL { try? FileManager.default.removeItem(at: outputURL) }
  }
}
