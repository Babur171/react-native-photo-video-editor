import UIKit

/// Small async image loader for the online-sticker sheet: in-memory + on-disk thumbnail cache,
/// plus a full-res "download and hand me a local file" path for the sticker that actually gets
/// inserted as a layer. Plain `URLSession`, no third-party dependency (matches the rest of the
/// codebase's zero-networking-dependency policy).
final class RemoteImageLoader {
  static let shared = RemoteImageLoader()

  private let memoryCache = NSCache<NSString, UIImage>()
  private let ioQueue = DispatchQueue(label: "com.photovideoeditor.remoteimageloader.io", qos: .userInitiated)
  private let session = URLSession.shared

  private init() {
    memoryCache.countLimit = 500
  }

  private var diskCacheDirectory: URL? {
    guard let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return nil }
    let dir = base.appendingPathComponent("openmoji", isDirectory: true)
    if !FileManager.default.fileExists(atPath: dir.path) {
      try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }
    return dir
  }

  private func diskCacheFile(forKey key: String) -> URL? {
    diskCacheDirectory?.appendingPathComponent(key)
  }

  // MARK: - Thumbnails (UICollectionViewCell-safe)

  /// Loads a thumbnail into `imageView`, guarding against cell reuse: the URL being loaded is
  /// stamped onto the image view via associated object, and the result is only applied if that
  /// stamp still matches once the load completes (standard fix for the "wrong image after scroll"
  /// bug with recycled `UICollectionViewCell`/`UITableViewCell` image views).
  func loadThumbnail(url: URL, into imageView: UIImageView) {
    Self.setLoadTag(url.absoluteString, on: imageView)
    imageView.image = nil

    let cacheKey = url.absoluteString as NSString
    if let cached = memoryCache.object(forKey: cacheKey) {
      imageView.image = cached
      return
    }

    let diskKey = "\(url.lastPathComponent)_thumb.png"
    ioQueue.async { [weak self] in
      guard let self else { return }
      if let diskURL = self.diskCacheFile(forKey: diskKey),
         let data = try? Data(contentsOf: diskURL),
         let image = UIImage(data: data) {
        self.memoryCache.setObject(image, forKey: cacheKey)
        DispatchQueue.main.async {
          guard Self.loadTag(on: imageView) == url.absoluteString else { return }
          imageView.image = image
        }
        return
      }

      self.session.dataTask(with: url) { data, response, _ in
        guard
          let data, let image = UIImage(data: data),
          let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode)
        else { return }
        self.memoryCache.setObject(image, forKey: cacheKey)
        if let diskURL = self.diskCacheFile(forKey: diskKey) {
          try? data.write(to: diskURL, options: .atomic)
        }
        DispatchQueue.main.async {
          guard Self.loadTag(on: imageView) == url.absoluteString else { return }
          imageView.image = image
        }
      }.resume()
    }
  }

  // MARK: - Full-res download

  /// Downloads (or returns the already-cached file for) the full-res sticker image. Completion
  /// always fires on the main thread.
  func downloadFull(url: URL, completion: @escaping (URL?, Error?) -> Void) {
    let diskKey = "\(url.lastPathComponent)_full.png"
    ioQueue.async { [weak self] in
      guard let self else { return }
      if let diskURL = self.diskCacheFile(forKey: diskKey), FileManager.default.fileExists(atPath: diskURL.path) {
        DispatchQueue.main.async { completion(diskURL, nil) }
        return
      }
      self.session.dataTask(with: url) { data, response, error in
        if let error {
          DispatchQueue.main.async { completion(nil, error) }
          return
        }
        guard
          let data,
          let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode)
        else {
          DispatchQueue.main.async { completion(nil, URLError(.badServerResponse)) }
          return
        }
        guard let diskURL = self.diskCacheFile(forKey: diskKey) else {
          DispatchQueue.main.async { completion(nil, URLError(.cannotCreateFile)) }
          return
        }
        do {
          try data.write(to: diskURL, options: .atomic)
          DispatchQueue.main.async { completion(diskURL, nil) }
        } catch {
          DispatchQueue.main.async { completion(nil, error) }
        }
      }.resume()
    }
  }

  // MARK: - Cell-reuse tagging

  private static var loadTagKey: UInt8 = 0

  private static func setLoadTag(_ tag: String, on imageView: UIImageView) {
    objc_setAssociatedObject(imageView, &loadTagKey, tag, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
  }

  private static func loadTag(on imageView: UIImageView) -> String? {
    objc_getAssociatedObject(imageView, &loadTagKey) as? String
  }
}
