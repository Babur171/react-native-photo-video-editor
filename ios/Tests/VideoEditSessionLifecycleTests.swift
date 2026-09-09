import AVFoundation
import XCTest
@testable import PhotoVideoEditor

@MainActor
final class VideoEditSessionLifecycleTests: XCTestCase {
  func testUnavailablePlaybackTimesReturnZero() {
    for time in [CMTime.invalid, .indefinite, .positiveInfinity, .negativeInfinity] {
      XCTAssertEqual(VideoEditSession.milliseconds(for: time), 0)
    }
  }

  func testPlaybackTimeConversion() {
    XCTAssertEqual(VideoEditSession.milliseconds(for: CMTime(value: 3, timescale: 2)), 1500)
    XCTAssertEqual(VideoEditSession.milliseconds(for: CMTime(value: -1, timescale: 1)), 0)
    XCTAssertEqual(VideoEditSession.milliseconds(for: .zero), 0)
  }

  func testReleaseClearsPlayerAndReadyCallback() {
    let session = VideoEditSession(sourceUri: "/tmp/pve-missing-lifecycle-test.mov")
    session.onClipsReady = { XCTFail("A released session must not deliver readiness") }
    session.release()
    session.release()
    XCTAssertNil(session.player.currentItem)
    XCTAssertNil(session.onClipsReady)
    XCTAssertEqual(session.currentPositionMs, 0)
  }
}
