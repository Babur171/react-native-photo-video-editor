import XCTest
@testable import PhotoVideoEditor

/// Mirrors the boundary-case coverage added to `PhotoLayerContractTest.kt` on Android.
///
/// Not built by the podspec (see `PhotoVideoEditor.podspec`'s `exclude_files`) — this
/// environment has no Xcode/macOS to create or run an XCTest target. Add this file to an
/// XCTest target in your own Xcode project (or CI) to execute it.
final class PhotoLayerVisibilityTests: XCTestCase {
  func testDefaultOverlayIsVisibleOnFinalFrame() {
    let layer = PhotoLayer(type: .text)
    XCTAssertTrue(layer.isActive(atMs: 10_000, durationMs: 10_000))
    XCTAssertEqual(layer.effectiveEndMs(durationMs: 10_000), 10_000)
  }

  /// `durationMs` is a separately-computed estimate (summed clip-trim durations) that can be a
  /// few ms shy of the real last decoded frame's timestamp due to frame-rate rounding. A default
  /// (no explicit end) overlay must not be hidden by that rounding gap.
  func testDefaultOverlayStaysVisibleDespiteMinorTimestampOvershoot() {
    let layer = PhotoLayer(type: .text)
    XCTAssertTrue(layer.isActive(atMs: 10_005, durationMs: 10_000))
    XCTAssertTrue(layer.isActive(atMs: 50_000, durationMs: 10_000))
  }

  func testExplicitEndAtOrPastDurationBehavesLikeDefault() {
    var layer = PhotoLayer(type: .text)
    layer.endMs = 10_000
    XCTAssertTrue(layer.isActive(atMs: 10_005, durationMs: 10_000))
  }

  func testExplicitTimingIsInclusiveAndRespected() {
    var layer = PhotoLayer(type: .text)
    layer.startMs = 2_000
    layer.endMs = 8_000
    XCTAssertFalse(layer.isActive(atMs: 1_999, durationMs: 10_000))
    XCTAssertTrue(layer.isActive(atMs: 2_000, durationMs: 10_000))
    XCTAssertTrue(layer.isActive(atMs: 8_000, durationMs: 10_000))
    XCTAssertFalse(layer.isActive(atMs: 8_001, durationMs: 10_000))
  }
}
