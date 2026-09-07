import AVFoundation
import CryptoKit
import UIKit

/// Bounded memory -> disk -> decoder pipeline for video timeline thumbnails.
final class TimelineThumbnailRepository {
  final class Request {
    private let lock = NSLock()
    private var cancelled = false

    func cancel() {
      lock.lock()
      cancelled = true
      lock.unlock()
    }

    fileprivate var isCancelled: Bool {
      lock.lock()
      defer { lock.unlock() }
      return cancelled
    }
  }

  private let memory = NSCache<NSString, UIImage>()
  private let decodeQueue: OperationQueue = {
    let queue = OperationQueue()
    queue.name = "com.photovideoeditor.timeline-thumbnails"
    queue.qualityOfService = .utility
    queue.maxConcurrentOperationCount = 2
    return queue
  }()
  private let diskQueue = DispatchQueue(label: "com.photovideoeditor.timeline-thumbnail-disk")
  private let cacheDirectory: URL
  private let maxDiskBytes: Int64

  init(maxDiskBytes: Int64 = 96 * 1024 * 1024) {
    self.maxDiskBytes = maxDiskBytes
    let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
    cacheDirectory = base.appendingPathComponent("pve_timeline_thumbnails", isDirectory: true)
    try? FileManager.default.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
    memory.totalCostLimit = min(64 * 1024 * 1024, max(16 * 1024 * 1024, Int(ProcessInfo.processInfo.physicalMemory / 16)))
    memory.countLimit = 96
  }

  @discardableResult
  func load(
    mediaID: String,
    asset: AVAsset,
    durationMs: Int64,
    count: Int = 12,
    size: CGSize,
    onThumbnail: @escaping (_ index: Int, _ image: UIImage) -> Void,
    onComplete: @escaping () -> Void
  ) -> Request {
    let request = Request()
    let safeCount = min(24, max(1, count))
    let pixelSize = CGSize(width: max(1, size.width), height: max(1, size.height))
    decodeQueue.addOperation { [weak self] in
      guard let self else { return }
      let generator = AVAssetImageGenerator(asset: asset)
      generator.appliesPreferredTrackTransform = true
      generator.maximumSize = pixelSize
      generator.requestedTimeToleranceBefore = CMTime(value: 100, timescale: 1000)
      generator.requestedTimeToleranceAfter = CMTime(value: 100, timescale: 1000)
      let step = max(1, durationMs) / Int64(safeCount)

      for index in 0..<safeCount {
        if request.isCancelled { break }
        autoreleasepool {
          let timestampMs = Int64(index) * step + step / 2
          let key = self.key(mediaID: mediaID, timestampMs: timestampMs, size: pixelSize)
          let image = self.memory.object(forKey: key as NSString)
            ?? self.readDisk(key: key)
            ?? self.decode(generator: generator, timestampMs: timestampMs)
          guard let image, !request.isCancelled else { return }
          self.memory.setObject(image, forKey: key as NSString, cost: image.memoryCost)
          self.writeDiskIfNeeded(key: key, image: image)
          DispatchQueue.main.async {
            if !request.isCancelled { onThumbnail(index, image) }
          }
        }
      }
      if !request.isCancelled { DispatchQueue.main.async { onComplete() } }
      self.diskQueue.async { self.trimDisk() }
    }
    return request
  }

  func close() {
    decodeQueue.cancelAllOperations()
    memory.removeAllObjects()
  }

  func clearMemory() {
    memory.removeAllObjects()
  }

  private func decode(generator: AVAssetImageGenerator, timestampMs: Int64) -> UIImage? {
    guard let cgImage = try? generator.copyCGImage(
      at: CMTime(value: timestampMs, timescale: 1000),
      actualTime: nil
    ) else { return nil }
    return UIImage(cgImage: cgImage)
  }

  private func readDisk(key: String) -> UIImage? {
    let url = cacheDirectory.appendingPathComponent(key).appendingPathExtension("jpg")
    guard let data = try? Data(contentsOf: url, options: .mappedIfSafe), let image = UIImage(data: data) else { return nil }
    try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: url.path)
    return image
  }

  private func writeDiskIfNeeded(key: String, image: UIImage) {
    let url = cacheDirectory.appendingPathComponent(key).appendingPathExtension("jpg")
    guard !FileManager.default.fileExists(atPath: url.path), let data = image.jpegData(compressionQuality: 0.82) else { return }
    try? data.write(to: url, options: .atomic)
  }

  private func trimDisk() {
    let keys: Set<URLResourceKey> = [.contentModificationDateKey, .fileSizeKey, .isRegularFileKey]
    guard let files = try? FileManager.default.contentsOfDirectory(
      at: cacheDirectory,
      includingPropertiesForKeys: Array(keys),
      options: [.skipsHiddenFiles]
    ) else { return }
    let entries = files.compactMap { url -> (URL, Date, Int64)? in
      guard let values = try? url.resourceValues(forKeys: keys), values.isRegularFile == true else { return nil }
      return (url, values.contentModificationDate ?? .distantPast, Int64(values.fileSize ?? 0))
    }.sorted { $0.1 < $1.1 }
    var bytes = entries.reduce(Int64(0)) { $0 + $1.2 }
    for entry in entries where bytes > maxDiskBytes {
      try? FileManager.default.removeItem(at: entry.0)
      bytes -= entry.2
    }
  }

  private func key(mediaID: String, timestampMs: Int64, size: CGSize) -> String {
    let digest = SHA256.hash(data: Data(mediaID.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
    return "timeline_\(digest)_\(timestampMs)_\(Int(size.width))x\(Int(size.height))"
  }
}

private extension UIImage {
  var memoryCost: Int {
    guard let image = cgImage else { return 1 }
    return image.bytesPerRow * image.height
  }
}
