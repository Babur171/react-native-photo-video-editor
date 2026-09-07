package com.photovideoeditor.photo.render

/**
 * Ordered layer stack with snapshot-based undo/redo. One entry is pushed per
 * *committed* operation (add/delete/duplicate/reorder, or a drag/slider
 * gesture's final value) — never per intermediate touch-move frame — so a
 * continuous drag does not flood the history.
 */
class PhotoLayerStack(private val historyLimit: Int = 50) {
  var layers: List<PhotoLayer> = emptyList()
    private set
  var revision: Long = 0
    private set

  private val undoStack = ArrayDeque<List<PhotoLayer>>()
  private val redoStack = ArrayDeque<List<PhotoLayer>>()

  val canUndo: Boolean get() = undoStack.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()

  /** Applies [mutation] as a single committed, undoable operation. */
  fun commit(mutation: (List<PhotoLayer>) -> List<PhotoLayer>) {
    pushUndo(layers)
    redoStack.clear()
    layers = mutation(layers)
    revision++
  }

  /** Updates the live layer list without creating a history entry — for in-progress drags. */
  fun updateLive(mutation: (List<PhotoLayer>) -> List<PhotoLayer>) {
    layers = mutation(layers)
    revision++
  }

  /** Records [previousLayers] (captured before a live-updated gesture began) as one undo step. */
  fun commitSnapshot(previousLayers: List<PhotoLayer>) {
    pushUndo(previousLayers)
    redoStack.clear()
    revision++
  }

  fun undo() {
    val previous = undoStack.removeLastOrNull() ?: return
    redoStack.addLast(layers)
    layers = previous
    revision++
  }

  fun redo() {
    val next = redoStack.removeLastOrNull() ?: return
    undoStack.addLast(layers)
    layers = next
    revision++
  }

  private fun pushUndo(snapshot: List<PhotoLayer>) {
    undoStack.addLast(snapshot)
    if (undoStack.size > historyLimit) undoStack.removeFirst()
  }
}
