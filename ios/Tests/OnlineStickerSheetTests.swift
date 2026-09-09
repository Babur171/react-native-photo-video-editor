import XCTest
import UIKit
@testable import PhotoVideoEditor

@MainActor
final class OnlineStickerSheetTests: XCTestCase {
  func testCustomStickerIsListedAndRenderedBeforeCellIsDisplayed() throws {
    let image = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8)).image { context in
      UIColor.red.setFill()
      context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
    }
    let data = try XCTUnwrap(image.pngData())
    let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
    try data.write(to: fileURL)
    defer { try? FileManager.default.removeItem(at: fileURL) }

    let sheet = OnlineStickerSheetViewController(runtimeStickers: [
      .init(id: "custom", uri: fileURL.absoluteString),
    ])
    sheet.loadViewIfNeeded()
    let root = try XCTUnwrap(sheet.view.subviews.compactMap { $0 as? UIStackView }.first)
    let collectionView = try XCTUnwrap(root.arrangedSubviews.compactMap { $0 as? UICollectionView }.first)
    XCTAssertGreaterThanOrEqual(sheet.collectionView(collectionView, numberOfItemsInSection: 0), 1)

    let indexPath = IndexPath(item: 0, section: 0)
    let cell = sheet.collectionView(collectionView, cellForItemAt: indexPath)
    XCTAssertNil(collectionView.indexPath(for: cell))
    let imageView = try XCTUnwrap(cell.contentView.subviews.compactMap { $0 as? UIImageView }.first)
    XCTAssertNotNil(imageView.image)
    cell.prepareForReuse()
    XCTAssertNil(imageView.image)
  }
}
