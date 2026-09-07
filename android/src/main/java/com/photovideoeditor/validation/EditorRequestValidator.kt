package com.photovideoeditor.validation

import com.photovideoeditor.model.EditorRequest

internal object EditorRequestValidator {
  fun validate(request: EditorRequest): String? = when {
    request.uri.isBlank() -> "E_INVALID_URI"
    request.mediaType != "photo" && request.mediaType != "video" -> "E_UNSUPPORTED_MEDIA_TYPE"
    else -> null
  }
}
