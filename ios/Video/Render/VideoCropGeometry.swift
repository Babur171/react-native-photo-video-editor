import AVFoundation
import CoreGraphics

/// Used by AVPlayer's video composition and export's layer instruction. This is the single source
/// of truth `videoMediaBounds()`/`videoNaturalSize` (in `PhotoVideoEditorViewController`) both derive
/// their size from, so overlay-relative coordinates stay consistent between preview and the real
/// composed frame — do not introduce a second, independently-computed size for either of those.
///
/// Known gap (not currently reachable from the UI — the video tool rail only exposes Crop/Text/
/// Stickers, not a whole-video "Rotate" tool): `VideoTransformState.rotationDegrees` (the top-level
/// rotate field, distinct from this function's `state.rotationDegrees`, which is the CROP sub-state's
/// own rotation) is applied to the player view as a cosmetic `.transform` only in
/// `applyVideoPreviewTransform` and never passed into this function, while `VideoExporter` bakes it
/// into the real output frame (swapping width/height for 90/270°). If that tool is ever wired back up,
/// `plan()` must also account for it, or preview and export will disagree.
enum VideoCropGeometry {
  static func plan(size: CGSize, state: PhotoTransformState, includeCrop: Bool) -> (transform:CGAffineTransform,size:CGSize) {
    let quarter = CGAffineTransform(rotationAngle:CGFloat(state.rotationDegrees) * .pi/180)
    let oriented = CGRect(origin:.zero,size:size).applying(quarter)
    let w = abs(oriented.width).rounded(), h = abs(oriented.height).rounded()
    var transform = quarter.concatenating(CGAffineTransform(translationX:-oriented.minX,y:-oriented.minY))
    if state.straightenDegrees != 0 {
      let scale = CropGeometry.fillScale(width:w,height:h,degrees:state.straightenDegrees)
      transform = transform.concatenating(CGAffineTransform(translationX:-w/2,y:-h/2))
        .concatenating(CGAffineTransform(rotationAngle:state.straightenDegrees * .pi/180))
        .concatenating(CGAffineTransform(scaleX:scale,y:scale))
        .concatenating(CGAffineTransform(translationX:w/2,y:h/2))
    }
    guard includeCrop else { return (transform,CGSize(width:w,height:h)) }
    let left = (state.cropLeft*w).rounded(), top = (state.cropTop*h).rounded()
    let width = max(2,(state.cropRight*w).rounded()-left), height = max(2,(state.cropBottom*h).rounded()-top)
    transform = transform.concatenating(CGAffineTransform(translationX:-left,y:-top))
    return (transform,CGSize(width:width,height:height))
  }

  static func previewComposition(asset: AVAsset, state: PhotoTransformState, includeCrop: Bool) -> AVVideoComposition? {
    guard let track = asset.tracks(withMediaType:.video).first else { return nil }
    let upright = track.naturalSize.applying(track.preferredTransform)
    let plan = plan(size:CGSize(width:abs(upright.width),height:abs(upright.height)),state:state,includeCrop:includeCrop)
    let composition = AVMutableVideoComposition()
    composition.renderSize = plan.size; composition.frameDuration = CMTime(value:1,timescale:30)
    let instruction = AVMutableVideoCompositionInstruction(); instruction.timeRange = CMTimeRange(start:.zero,duration:asset.duration)
    let layer = AVMutableVideoCompositionLayerInstruction(assetTrack:track)
    layer.setTransform(track.preferredTransform.concatenating(plan.transform),at:.zero)
    instruction.layerInstructions = [layer]; composition.instructions = [instruction]
    return composition
  }
}
