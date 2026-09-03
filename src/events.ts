import NativePhotoVideoEditor from './NativePhotoVideoEditor';
import { PhotoVideoEditorError } from './errors';
import type {
  EditorEvent,
  EditorEventListener,
  EditorSubscription,
  ExportProgressEvent,
} from './types';

export function addEditorEventListener(
  listener: EditorEventListener
): EditorSubscription {
  if (typeof listener !== 'function')
    throw new PhotoVideoEditorError(
      'E_INVALID_OPTIONS',
      'Editor listener must be a function.'
    );
  return NativePhotoVideoEditor.onEditorEvent((payload) => {
    try {
      listener(JSON.parse(payload) as EditorEvent);
    } catch {
      /* malformed native event */
    }
  });
}

/** Subscribes to real native export updates. Milestone one emits none. */
export function addExportProgressListener(
  listener: (event: ExportProgressEvent) => void
): EditorSubscription {
  if (typeof listener !== 'function')
    throw new PhotoVideoEditorError(
      'E_INVALID_OPTIONS',
      'Progress listener must be a function.'
    );
  return NativePhotoVideoEditor.onExportProgress((payload) => {
    try {
      listener(JSON.parse(payload) as ExportProgressEvent);
    } catch {
      /* Ignore malformed native events. */
    }
  });
}
