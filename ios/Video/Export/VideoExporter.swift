import AVFoundation
import CoreImage
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
    let compositionAudioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)

    var sourceVideoTrack: AVAssetTrack?
    var cursor = CMTime.zero
    var hasAudio = false
    for clip in clips {
      let clipAsset = assetProvider(clip)
      guard let clipVideoTrack = clipAsset.tracks(withMediaType: .video).first else {
        onError(VideoExportError(code: "E_SOURCE_UNREADABLE", message: "A clip's source video could not be decoded."))
        return
      }
      if sourceVideoTrack == nil {
        sourceVideoTrack = clipVideoTrack
        compositionVideoTrack.preferredTransform = clipVideoTrack.preferredTransform
      }
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
        if (try? compositionAudioTrack.insertTimeRange(range, of: clipAudioTrack, at: cursor)) != nil {
          hasAudio = true
        }
      }
      cursor = CMTimeAdd(cursor, range.duration)
    }
    if !hasAudio, let compositionAudioTrack {
      composition.removeTrack(compositionAudioTrack)
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

    // Crop around the transformed video's center. The same normalized layer
    // coordinates are then evaluated against this final output rectangle.
    if let targetRatio = state.aspectRatio, targetRatio > 0 {
      let currentRatio = renderWidth / renderHeight
      if currentRatio > targetRatio {
        let croppedWidth = renderHeight * targetRatio
        transform = transform.concatenating(CGAffineTransform(translationX: -(renderWidth - croppedWidth) / 2, y: 0))
        renderWidth = croppedWidth
      } else if currentRatio < targetRatio {
        let croppedHeight = renderWidth / targetRatio
        transform = transform.concatenating(CGAffineTransform(translationX: 0, y: -(renderHeight - croppedHeight) / 2))
        renderHeight = croppedHeight
      }
    }

    let cropPlan = VideoCropGeometry.plan(size:CGSize(width:renderWidth,height:renderHeight),state:state.crop,includeCrop:true)
    transform = transform.concatenating(cropPlan.transform)
    renderWidth = max(2, floor(cropPlan.size.width / 2) * 2)
    renderHeight = max(2, floor(cropPlan.size.height / 2) * 2)

    let renderSize = CGSize(width: renderWidth, height: renderHeight)
    let visibleLayers = layers.filter(\.visible)
    let videoComposition: AVVideoComposition

    if visibleLayers.isEmpty {
      let mutableComposition = AVMutableVideoComposition()
      mutableComposition.renderSize = renderSize
      mutableComposition.frameDuration = CMTime(value: 1, timescale: 30)

      let instruction = AVMutableVideoCompositionInstruction()
      instruction.timeRange = CMTimeRange(start: .zero, duration: timeRange.duration)
      let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: compositionVideoTrack)
      layerInstruction.setTransform(transform, at: .zero)
      instruction.layerInstructions = [layerInstruction]
      mutableComposition.instructions = [instruction]
      videoComposition = mutableComposition
    } else {
      let longestEdge = max(renderSize.width, renderSize.height)
      let overlayScale = min(1, 2560 / max(longestEdge, 1))
      let overlayRenderSize = CGSize(
        width: max(2, floor(renderSize.width * overlayScale)),
        height: max(2, floor(renderSize.height * overlayScale))
      )
      let renderer = PhotoEditSession.pixelRenderer(size: overlayRenderSize)
      let transparentBase = renderer.image { _ in }

      let overlayTransform = CGAffineTransform(
        scaleX: renderSize.width / overlayRenderSize.width,
        y: renderSize.height / overlayRenderSize.height
      )

      let lock = NSLock()
      var overlayCache: [String: CIImage] = [:]

      let mutableComposition = AVMutableVideoComposition(asset: composition) { request in
        let timeMs = Int64(CMTimeGetSeconds(request.compositionTime) * 1000)
        var source = request.sourceImage

        if source.extent.origin != .zero {
          source = source.transformed(by: CGAffineTransform(translationX: -source.extent.origin.x, y: -source.extent.origin.y))
        }

        if normalizedRotation != 0 {
          let radians = CGFloat(normalizedRotation) * .pi / 180
          source = source.transformed(by: CGAffineTransform(rotationAngle: radians))
          source = source.transformed(by: CGAffineTransform(translationX: -source.extent.origin.x, y: -source.extent.origin.y))
        }

        if let targetRatio = state.aspectRatio, targetRatio > 0 {
          let currentRatio = source.extent.width / source.extent.height
          if currentRatio > targetRatio {
            let croppedWidth = source.extent.height * targetRatio
            let cropRect = CGRect(
              x: source.extent.origin.x + (source.extent.width - croppedWidth) / 2,
              y: source.extent.origin.y,
              width: croppedWidth,
              height: source.extent.height
            )
            source = source.cropped(to: cropRect).transformed(by: CGAffineTransform(translationX: -cropRect.origin.x, y: -cropRect.origin.y))
          } else if currentRatio < targetRatio {
            let croppedHeight = source.extent.width / targetRatio
            let cropRect = CGRect(
              x: source.extent.origin.x,
              y: source.extent.origin.y + (source.extent.height - croppedHeight) / 2,
              width: source.extent.width,
              height: croppedHeight
            )
            source = source.cropped(to: cropRect).transformed(by: CGAffineTransform(translationX: -cropRect.origin.x, y: -cropRect.origin.y))
          }
        }

        if state.crop.rotationDegrees != 0 || state.crop.straightenDegrees != 0 || state.crop.cropLeft > 0 || state.crop.cropTop > 0 || state.crop.cropRight < 1 || state.crop.cropBottom < 1 {
          let cropPlan = VideoCropGeometry.plan(size: CGSize(width: source.extent.width, height: source.extent.height), state: state.crop, includeCrop: true)
          let cropRect = CGRect(
            x: source.extent.origin.x + (source.extent.width - cropPlan.size.width) / 2,
            y: source.extent.origin.y + (source.extent.height - cropPlan.size.height) / 2,
            width: cropPlan.size.width,
            height: cropPlan.size.height
          )
          source = source.cropped(to: cropRect).transformed(by: CGAffineTransform(translationX: -cropRect.origin.x, y: -cropRect.origin.y))
        }

        let activeLayers = visibleLayers.filter { $0.isActive(atMs: timeMs, durationMs: durationMs) }
        if !activeLayers.isEmpty {
          let key = activeLayers.map { "\($0.id)" }.joined(separator: ",")
          lock.lock()
          let cached = overlayCache[key]
          lock.unlock()

          let overlayCI: CIImage?
          if let cached {
            overlayCI = cached
          } else {
            let image = PhotoLayerRenderer.render(transparentBase, layers: activeLayers) { uri in
              SourceResolver.resolvePath(sourceUri: uri, tempPrefix: "pve_video_layer_export").flatMap { UIImage(contentsOfFile: $0) }
            }
            if let cgImage = image.cgImage {
              let ci = CIImage(cgImage: cgImage).transformed(by: overlayTransform)
              lock.lock()
              overlayCache[key] = ci
              lock.unlock()
              overlayCI = ci
            } else {
              overlayCI = nil
            }
          }

          if let overlayCI {
            source = overlayCI.composited(over: source)
          }
        }

        let output = source.cropped(to: CGRect(origin: .zero, size: renderSize))
        request.finish(with: output, context: nil)
      }

      mutableComposition.renderSize = renderSize
      mutableComposition.frameDuration = CMTime(value: 1, timescale: 30)
      videoComposition = mutableComposition
    }

    let outputDir = FileManager.default.temporaryDirectory.appendingPathComponent("photovideoeditor", isDirectory: true)
    try? FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
    let output = outputDir.appendingPathComponent(UUID().uuidString + ".mp4")
    outputURL = output

    guard let session = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else {
      cleanup()
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
      self.cleanup()
      switch session.status {
      case .completed:
        self.outputURL = nil
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

  private func cleanup() {
    progressTimer?.invalidate()
    progressTimer = nil
  }

  func cancel() {
    exportSession?.cancelExport()
    cleanup()
    if let outputURL { try? FileManager.default.removeItem(at: outputURL) }
  }
}
