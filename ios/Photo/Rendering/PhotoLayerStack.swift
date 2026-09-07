import Foundation

/// Ordered layer stack with snapshot-based undo/redo. One entry is pushed per
/// *committed* operation (add/delete/duplicate/reorder, or a drag/slider
/// gesture's final value) — never per intermediate touch-move frame — so a
/// continuous drag does not flood the history. Mirrors `PhotoLayerStack.kt`.
final class PhotoLayerStack {
  private(set) var layers: [PhotoLayer] = []
  private(set) var revision: UInt64 = 0
  private var undoStack: [[PhotoLayer]] = []
  private var redoStack: [[PhotoLayer]] = []
  private let historyLimit: Int

  init(historyLimit: Int = 50) {
    self.historyLimit = historyLimit
  }

  var canUndo: Bool { !undoStack.isEmpty }
  var canRedo: Bool { !redoStack.isEmpty }

  /// Applies `mutation` as a single committed, undoable operation.
  func commit(_ mutation: ([PhotoLayer]) -> [PhotoLayer]) {
    pushUndo(layers)
    redoStack.removeAll()
    layers = mutation(layers)
    revision &+= 1
  }

  /// Updates the live layer list without creating a history entry — for in-progress drags.
  func updateLive(_ mutation: ([PhotoLayer]) -> [PhotoLayer]) {
    layers = mutation(layers)
    revision &+= 1
  }

  /// Records `previousLayers` (captured before a live-updated gesture began) as one undo step.
  func commitSnapshot(_ previousLayers: [PhotoLayer]) {
    pushUndo(previousLayers)
    redoStack.removeAll()
    revision &+= 1
  }

  func undo() {
    guard let previous = undoStack.popLast() else { return }
    redoStack.append(layers)
    layers = previous
    revision &+= 1
  }

  func redo() {
    guard let next = redoStack.popLast() else { return }
    undoStack.append(layers)
    layers = next
    revision &+= 1
  }

  private func pushUndo(_ snapshot: [PhotoLayer]) {
    undoStack.append(snapshot)
    if undoStack.count > historyLimit { undoStack.removeFirst() }
  }
}
