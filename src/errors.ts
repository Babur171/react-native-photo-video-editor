/** Stable error codes exposed across Android and iOS. */
export type PhotoVideoEditorErrorCode =
  | 'E_INVALID_OPTIONS'
  | 'E_INVALID_URI'
  | 'E_UNSUPPORTED_MEDIA_TYPE'
  | 'E_SOURCE_NOT_FOUND'
  | 'E_SOURCE_UNREADABLE'
  | 'E_UNSUPPORTED_FORMAT'
  | 'E_EDITOR_ALREADY_OPEN'
  | 'E_EDITOR_UNAVAILABLE'
  | 'E_EDITOR_CANCELLED'
  | 'E_EXPORT_FAILED'
  | 'E_EXPORT_IN_PROGRESS'
  | 'E_EXPORT_CANCELLED'
  | 'E_INSUFFICIENT_STORAGE'
  | 'E_OUT_OF_MEMORY'
  | 'E_CODEC_UNAVAILABLE'
  | 'E_PROJECT_INVALID'
  | 'E_PROJECT_VERSION_UNSUPPORTED'
  | 'E_PERMISSION_DENIED'
  | 'E_INTERNAL';

const codes = new Set<PhotoVideoEditorErrorCode>([
  'E_INVALID_OPTIONS',
  'E_INVALID_URI',
  'E_UNSUPPORTED_MEDIA_TYPE',
  'E_SOURCE_NOT_FOUND',
  'E_SOURCE_UNREADABLE',
  'E_UNSUPPORTED_FORMAT',
  'E_EDITOR_ALREADY_OPEN',
  'E_EDITOR_UNAVAILABLE',
  'E_EDITOR_CANCELLED',
  'E_EXPORT_FAILED',
  'E_EXPORT_IN_PROGRESS',
  'E_EXPORT_CANCELLED',
  'E_INSUFFICIENT_STORAGE',
  'E_OUT_OF_MEMORY',
  'E_CODEC_UNAVAILABLE',
  'E_PROJECT_INVALID',
  'E_PROJECT_VERSION_UNSUPPORTED',
  'E_PERMISSION_DENIED',
  'E_INTERNAL',
]);

/** Error thrown by every public asynchronous operation. */
export class PhotoVideoEditorError extends Error {
  /** Stable machine-readable code. */ readonly code: PhotoVideoEditorErrorCode;
  /** Sanitized original native metadata, when available. */ readonly native?: unknown;
  constructor(
    code: PhotoVideoEditorErrorCode,
    message: string,
    native?: unknown
  ) {
    super(message);
    this.name = 'PhotoVideoEditorError';
    this.code = code;
    this.native = native;
  }
}

/** Converts unknown native rejection values to the stable public error class. */
export function normalizeEditorError(error: unknown): PhotoVideoEditorError {
  if (error instanceof PhotoVideoEditorError) return error;
  const value =
    typeof error === 'object' && error !== null
      ? (error as { code?: unknown; message?: unknown })
      : {};
  const code =
    typeof value.code === 'string' &&
    codes.has(value.code as PhotoVideoEditorErrorCode)
      ? (value.code as PhotoVideoEditorErrorCode)
      : 'E_INTERNAL';
  const message =
    typeof value.message === 'string' && value.message.trim()
      ? value.message
      : 'The native editor failed unexpectedly.';
  return new PhotoVideoEditorError(code, message, { code: value.code });
}
