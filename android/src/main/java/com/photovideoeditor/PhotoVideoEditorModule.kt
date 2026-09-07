package com.photovideoeditor

import android.app.Activity
import android.content.Intent
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.BaseActivityEventListener
import com.photovideoeditor.editor.PhotoVideoEditorActivity
import com.photovideoeditor.model.EditorRequest
import com.photovideoeditor.validation.EditorRequestValidator
import org.json.JSONObject

class PhotoVideoEditorModule(reactContext: ReactApplicationContext) :
  NativePhotoVideoEditorSpec(reactContext) {

  private var editorPromise: Promise? = null
  private var pendingUri: String? = null
  private var pendingType: String? = null
  private var pendingMimeType: String? = null

  private val activityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
      if (requestCode != EDITOR_REQUEST_CODE) return
      val promise = editorPromise ?: return
      editorPromise = null

      if (resultCode == PhotoVideoEditorActivity.RESULT_ERROR) {
        val code = data?.getStringExtra(PhotoVideoEditorActivity.EXTRA_ERROR_CODE) ?: "E_INTERNAL"
        promise.reject(code, data?.getStringExtra(PhotoVideoEditorActivity.EXTRA_ERROR) ?: "The editor could not open the selected media.")
        pendingUri = null
        pendingType = null
        pendingMimeType = null
        return
      }

      val cancelled = resultCode != Activity.RESULT_OK
      val result = JSONObject()
        .put("uri", data?.getStringExtra(PhotoVideoEditorActivity.EXTRA_URI) ?: pendingUri.orEmpty())
        .put("type", data?.getStringExtra(PhotoVideoEditorActivity.EXTRA_TYPE) ?: pendingType.orEmpty())
        .put("cancelled", cancelled)
      if (!cancelled) {
        val exportedMimeType = data?.getStringExtra(PhotoVideoEditorActivity.EXTRA_MIME_TYPE) ?: pendingMimeType
        exportedMimeType?.let { result.put("mimeType", it) }
        if (data?.hasExtra(PhotoVideoEditorActivity.EXTRA_WIDTH) == true) {
          result.put("width", data.getIntExtra(PhotoVideoEditorActivity.EXTRA_WIDTH, 0))
          result.put("height", data.getIntExtra(PhotoVideoEditorActivity.EXTRA_HEIGHT, 0))
          result.put("fileSize", data.getLongExtra(PhotoVideoEditorActivity.EXTRA_FILE_SIZE, 0L))
        }
        if (data?.hasExtra(PhotoVideoEditorActivity.EXTRA_DURATION) == true) {
          result.put("duration", data.getLongExtra(PhotoVideoEditorActivity.EXTRA_DURATION, 0L))
        }
      }
      pendingUri = null
      pendingType = null
      pendingMimeType = null
      emitOnEditorEvent(JSONObject().put("type", "editorClosed").toString())
      promise.resolve(result.toString())
    }
  }

  init {
    reactContext.addActivityEventListener(activityEventListener)
  }

  override fun openEditor(request: String, promise: Promise) {
    try {
      val payload = JSONObject(request)
      val source = payload.optJSONObject("source")
      if (source == null) {
        promise.reject("E_INVALID_OPTIONS", "options.source is required.")
        return
      }
      val uri = source.optString("uri").trim()
      val type = source.optString("type")
      val mimeType = source.optString("mimeType").takeIf { it.isNotBlank() }
      val validationCode = EditorRequestValidator.validate(EditorRequest(uri, type, mimeType))
      if (validationCode != null) {
        val message = if (validationCode == "E_INVALID_URI") "source.uri must be a non-empty string." else "source.type must be photo or video."
        promise.reject(validationCode, message)
        return
      }
      val activity = reactApplicationContext.currentActivity
      if (activity == null) {
        promise.reject("E_EDITOR_UNAVAILABLE", "The editor requires a foreground Android Activity.")
        return
      }
      if (editorPromise != null) {
        promise.reject("E_EDITOR_ALREADY_OPEN", "Another editor is already open.")
        return
      }

      editorPromise = promise
      pendingUri = uri
      pendingType = type
      pendingMimeType = mimeType
      val intent = Intent(activity, PhotoVideoEditorActivity::class.java)
        .putExtra(PhotoVideoEditorActivity.EXTRA_URI, uri)
        .putExtra(PhotoVideoEditorActivity.EXTRA_TYPE, type)
        .putExtra(PhotoVideoEditorActivity.EXTRA_MIME_TYPE, mimeType)
        .putExtra(PhotoVideoEditorActivity.EXTRA_REQUEST, request)
      activity.runOnUiThread {
        try {
          activity.startActivityForResult(intent, EDITOR_REQUEST_CODE)
          emitOnEditorEvent(JSONObject().put("type", "editorOpened").toString())
        } catch (error: Exception) {
          editorPromise = null
          pendingUri = null
          pendingType = null
          pendingMimeType = null
          promise.reject("E_INTERNAL", "Unable to launch the editor.", error)
        }
      }
    } catch (_: Exception) {
      promise.reject("E_INVALID_OPTIONS", "Editor options must be valid serialized JSON.")
    }
  }

  override fun cancelExport(jobId: String?, promise: Promise) = promise.resolve(false)

  override fun isAvailable(): Boolean = true

  companion object {
    const val NAME = NativePhotoVideoEditorSpec.NAME
    private const val EDITOR_REQUEST_CODE = 41091
  }
}
