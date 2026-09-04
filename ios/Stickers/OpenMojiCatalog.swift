import Foundation

/// A single entry from OpenMoji's `openmoji.json` index that we care about.
/// Mirrors Android's `OpenMojiCatalog.Entry` in spirit (same field set).
struct OpenMojiEntry {
  let emoji: String
  let hexcode: String
  let group: String
  let annotation: String
  let tags: String
}

/// Fetches, disk-caches, and serves the OpenMoji sticker index
/// (`https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/data/openmoji.json`).
///
/// No third-party networking/JSON dependency is used — this codebase is deliberately
/// dependency-light (see `stickerAssets: [[String: Any]]` in `PhotoVideoEditorViewController`
/// for the existing dictionary-based JSON precedent). Plain `URLSession` + `JSONSerialization`.
final class OpenMojiCatalog {
  static let shared = OpenMojiCatalog()

  /// Fixed, sensible display order for OpenMoji's `group` values.
  static let orderedGroups: [String] = [
    "smileys-emotion",
    "people-body",
    "animals-nature",
    "food-drink",
    "travel-places",
    "activities",
    "objects",
    "symbols",
    "flags",
  ]

  private static let indexURL = URL(string: "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/data/openmoji.json")!
  private static let cacheFileName = "openmoji_index.json"
  private static let cacheTimestampKey = "OpenMojiCatalog.cacheTimestamp"
  private static let cacheMaxAgeSeconds: TimeInterval = 7 * 24 * 60 * 60 // 7 days

  private let queue = DispatchQueue(label: "com.photovideoeditor.openmojicatalog", qos: .userInitiated)
  private var entries: [OpenMojiEntry]?
  private var isLoading = false
  private var pendingCompletions: [([OpenMojiEntry]?, Error?) -> Void] = []

  private init() {}

  private var cacheFileURL: URL? {
    FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
      .appendingPathComponent(Self.cacheFileName)
  }

  enum CatalogError: Error {
    case network(Error)
    case badResponse
    case emptyCache
  }

  /// Loads the catalog (cache-first, network fallback with cache-refresh) and calls `completion`
  /// on the main thread. Safe to call multiple times concurrently; duplicate in-flight calls are coalesced.
  func load(completion: @escaping ([OpenMojiEntry]?, Error?) -> Void) {
    if let entries {
      DispatchQueue.main.async { completion(entries, nil) }
      return
    }
    queue.async { [weak self] in
      guard let self else { return }
      self.pendingCompletions.append(completion)
      if self.isLoading { return }
      self.isLoading = true
      self.loadLocked()
    }
  }

  /// Must be called on `queue`. Decides whether to use the on-disk cache or fetch fresh, then
  /// fans results out to every completion queued while the load was in flight.
  private func loadLocked() {
    let cacheAge = self.cacheAgeSeconds()
    if let cacheAge, cacheAge < Self.cacheMaxAgeSeconds, let cached = self.readCache() {
      self.finishLoad(entries: cached, error: nil)
      return
    }
    self.fetchFromNetwork { [weak self] data, error in
      guard let self else { return }
      self.queue.async {
        if let data {
          self.writeCache(data)
          let parsed = self.parse(data)
          self.finishLoad(entries: parsed, error: parsed.isEmpty ? CatalogError.badResponse : nil)
        } else if let cached = self.readCache() {
          // Network failed but we have a cache of any age — fall back to it.
          self.finishLoad(entries: cached, error: nil)
        } else {
          self.finishLoad(entries: nil, error: error ?? CatalogError.emptyCache)
        }
      }
    }
  }

  /// Must be called on `queue`.
  private func finishLoad(entries: [OpenMojiEntry]?, error: Error?) {
    if let entries { self.entries = entries }
    let completions = self.pendingCompletions
    self.pendingCompletions = []
    self.isLoading = false
    DispatchQueue.main.async {
      completions.forEach { $0(entries, entries == nil ? error : nil) }
    }
  }

  private func fetchFromNetwork(completion: @escaping (Data?, Error?) -> Void) {
    let task = URLSession.shared.dataTask(with: Self.indexURL) { data, response, error in
      if let error {
        completion(nil, CatalogError.network(error))
        return
      }
      guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode), let data else {
        completion(nil, CatalogError.badResponse)
        return
      }
      completion(data, nil)
    }
    task.resume()
  }

  private func cacheAgeSeconds() -> TimeInterval? {
    let timestamp = UserDefaults.standard.double(forKey: Self.cacheTimestampKey)
    guard timestamp > 0 else { return nil }
    return Date().timeIntervalSince1970 - timestamp
  }

  private func readCache() -> [OpenMojiEntry]? {
    guard let url = cacheFileURL, let data = try? Data(contentsOf: url) else { return nil }
    let parsed = parse(data)
    return parsed.isEmpty ? nil : parsed
  }

  private func writeCache(_ data: Data) {
    guard let url = cacheFileURL else { return }
    do {
      try data.write(to: url, options: .atomic)
      UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: Self.cacheTimestampKey)
    } catch {
      // Best-effort cache; ignore write failures.
    }
  }

  private func parse(_ data: Data) -> [OpenMojiEntry] {
    guard let raw = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
    return raw.compactMap { dict in
      guard
        let hexcode = dict["hexcode"] as? String, !hexcode.isEmpty,
        let group = dict["group"] as? String
      else { return nil }
      let emoji = dict["emoji"] as? String ?? ""
      let annotation = dict["annotation"] as? String ?? ""
      let tags = dict["tags"] as? String ?? ""
      return OpenMojiEntry(emoji: emoji, hexcode: hexcode, group: group, annotation: annotation, tags: tags)
    }
  }

  // MARK: - Query surface

  func groups() -> [String] {
    guard let entries else { return [] }
    let present = Set(entries.map { $0.group })
    return Self.orderedGroups.filter { present.contains($0) }
  }

  func byGroup(_ group: String) -> [OpenMojiEntry] {
    (entries ?? []).filter { $0.group == group }
  }

  func search(_ query: String) -> [OpenMojiEntry] {
    let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    guard !needle.isEmpty else { return entries ?? [] }
    return (entries ?? []).filter {
      $0.annotation.lowercased().contains(needle) || $0.tags.lowercased().contains(needle)
    }
  }

  func thumbnailURL(_ hexcode: String) -> URL {
    URL(string: "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/72x72/\(hexcode).png")!
  }

  func fullURL(_ hexcode: String) -> URL {
    URL(string: "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/618x618/\(hexcode).png")!
  }
}
