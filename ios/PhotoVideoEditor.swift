import Foundation
import React
import UIKit

@objc(PhotoVideoEditorSwift)
public class PhotoVideoEditorSwift: NSObject {
  private var editorOpen = false

  @objc public func openEditor(
    _ request: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard let data = request.data(using: .utf8),
          let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let source = payload["source"] as? [String: Any] else {
      reject("E_INVALID_OPTIONS", "options.source is required.", nil); return
    }
    let uri = (source["uri"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    guard !uri.isEmpty else { reject("E_INVALID_URI", "source.uri must be a non-empty string.", nil); return }
    guard let type = source["type"] as? String, type == "photo" || type == "video" else {
      reject("E_UNSUPPORTED_MEDIA_TYPE", "source.type must be photo or video.", nil); return
    }
    guard !editorOpen else { reject("E_EDITOR_ALREADY_OPEN", "Another editor is already open.", nil); return }
    guard let presenter = RCTPresentedViewController() else { reject("E_EDITOR_UNAVAILABLE", "The editor requires a foreground view controller.", nil); return }
    editorOpen = true
    let doneButtonText = payload["doneButtonText"] as? String ?? payload["exportButtonText"] as? String
    let controller = PhotoVideoEditorViewController(
      uri: uri,
      mediaType: type,
      features: payload["features"] as? [String: Any] ?? [:],
      theme: payload["theme"] as? [String: Any] ?? [:],
      exportOptions: payload["export"] as? [String: Any] ?? [:],
      stickerAssets: payload["stickerAssets"] as? [[String: Any]] ?? [],
      initialStickerIds: payload["initialStickerIds"] as? [String] ?? [],
      doneButtonText: doneButtonText
    )
    controller.completion = { [weak self, weak controller] outcome in
      guard let self else { return }
      self.editorOpen = false
      DispatchQueue.main.async {
        controller?.dismiss(animated: true) {
          switch outcome {
          case .cancelled:
            var result: [String: Any] = ["uri": uri, "type": type, "cancelled": true]
            if let mimeType = source["mimeType"] as? String, !mimeType.isEmpty { result["mimeType"] = mimeType }
            PhotoVideoEditorSwift.resolveResult(result, resolve: resolve, reject: reject)
          case let .success(resultUri, mimeType, width, height, fileSize, durationMs):
            var result: [String: Any] = ["uri": resultUri, "type": type, "cancelled": false]
            let resolvedMimeType = mimeType ?? (source["mimeType"] as? String)
            if let resolvedMimeType, !resolvedMimeType.isEmpty { result["mimeType"] = resolvedMimeType }
            if let width { result["width"] = width }
            if let height { result["height"] = height }
            if let fileSize { result["fileSize"] = fileSize }
            if let durationMs { result["duration"] = durationMs }
            PhotoVideoEditorSwift.resolveResult(result, resolve: resolve, reject: reject)
          case let .failure(code, message):
            reject(code, message, nil)
          }
        }
      }
    }
    DispatchQueue.main.async { presenter.present(controller, animated: true) }
  }

  @objc public func cancelExport(_ jobId: String?, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) { resolve(false) }
  @objc public func isAvailable() -> NSNumber { true }

  private static func resolveResult(_ result: [String: Any], resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    guard let output = try? JSONSerialization.data(withJSONObject: result), let json = String(data: output, encoding: .utf8) else {
      reject("E_INTERNAL", "Unable to serialize editor result.", nil)
      return
    }
    resolve(json)
  }
}
