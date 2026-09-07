import NativePhotoVideoEditor from './NativePhotoVideoEditor';
import { PhotoVideoEditorError, normalizeEditorError } from './errors';
import type {
  EditorOptions,
  EditorResult,
  ExportQuality,
  ImageExportFormat,
  MediaType,
  PhotoEditorOptions,
  VideoExportFormat,
  VideoEditorOptions,
} from './types';

export { addEditorEventListener, addExportProgressListener } from './events';
export { PhotoVideoEditorError } from './errors';
export type * from './types';
export { EDITOR_PROJECT_SCHEMA_VERSION } from './project';
export type { EditorProject } from './project';

const mediaTypes = new Set<MediaType>(['photo', 'video']);
const qualities = new Set<ExportQuality>(['low', 'medium', 'high', 'original']);
const imageFormats = new Set<ImageExportFormat>(['jpeg', 'png', 'webp']);
const videoFormats = new Set<VideoExportFormat>(['mp4', 'mov']);
const defaultFeatures = {
  crop: true,
  rotate: true,
  trim: true,
  filters: true,
  text: true,
  stickers: true,
  draw: true,
  overlays: true,
};

function invalid(message: string): never {
  throw new PhotoVideoEditorError('E_INVALID_OPTIONS', message);
}
function positive(value: number | undefined, name: string): void {
  if (value !== undefined && (!Number.isFinite(value) || value <= 0))
    invalid(`${name} must be a positive number.`);
}
function normalizeOptions(input: EditorOptions): EditorOptions {
  if (
    !input ||
    typeof input !== 'object' ||
    !input.source ||
    typeof input.source !== 'object'
  )
    invalid('options.source is required.');
  if (typeof input.source.uri !== 'string' || !input.source.uri.trim())
    throw new PhotoVideoEditorError(
      'E_INVALID_URI',
      'source.uri must be a non-empty string.'
    );
  if (!mediaTypes.has(input.source.type))
    throw new PhotoVideoEditorError(
      'E_UNSUPPORTED_MEDIA_TYPE',
      'source.type must be photo or video.'
    );
  const output = input.export ?? {};
  if (output.quality !== undefined && !qualities.has(output.quality))
    invalid('export.quality is unsupported.');
  if (output.imageFormat !== undefined && !imageFormats.has(output.imageFormat))
    invalid('export.imageFormat is unsupported.');
  if (output.videoFormat !== undefined && !videoFormats.has(output.videoFormat))
    invalid('export.videoFormat is unsupported.');
  positive(output.maxWidth, 'export.maxWidth');
  positive(output.maxHeight, 'export.maxHeight');
  positive(output.videoBitrate, 'export.videoBitrate');
  positive(output.frameRate, 'export.frameRate');
  if (input.source.type === 'photo' && output.videoFormat !== undefined)
    invalid('videoFormat is only valid for video sources.');
  if (input.source.type === 'video' && output.imageFormat !== undefined)
    invalid('imageFormat is only valid for photo sources.');
  if (input.initialStickerId !== undefined) {
    if (
      typeof input.initialStickerId !== 'string' ||
      !input.initialStickerId.trim()
    )
      invalid('initialStickerId must be a non-empty string.');
    const matches = Array.isArray(input.stickerAssets)
      ? input.stickerAssets.filter(
          (asset) => asset?.id === input.initialStickerId
        )
      : [];
    if (
      matches.length !== 1 ||
      typeof matches[0]?.uri !== 'string' ||
      !matches[0].uri.trim()
    )
      invalid(
        'initialStickerId must match exactly one stickerAssets entry with a non-empty uri.'
      );
  }
  const features = { ...defaultFeatures, ...input.features };
  if (input.source.type === 'photo') {
    features.trim = false;
  }
  return {
    ...input,
    source: { ...input.source, uri: input.source.uri.trim() },
    features,
    export: { quality: 'high', preserveMetadata: true, ...output },
    saveToGallery: input.saveToGallery ?? false,
  };
}

/** Opens the full-screen native editor selected by source.type. */
export async function openEditor(
  options: EditorOptions
): Promise<EditorResult> {
  try {
    return JSON.parse(
      await NativePhotoVideoEditor.openEditor(
        JSON.stringify(normalizeOptions(options))
      )
    ) as EditorResult;
  } catch (error) {
    throw normalizeEditorError(error);
  }
}
export function openPhotoEditor(
  options: PhotoEditorOptions
): Promise<EditorResult> {
  return openEditor(options);
}
export function openVideoEditor(
  options: VideoEditorOptions
): Promise<EditorResult> {
  return openEditor(options);
}
/** Requests cancellation. Returns false in milestone one because no export job exists. */
export async function cancelExport(jobId?: string): Promise<boolean> {
  try {
    return await NativePhotoVideoEditor.cancelExport(jobId ?? null);
  } catch (error) {
    throw normalizeEditorError(error);
  }
}
/** Returns whether the linked native module is available on this device. */
export function isAvailable(): boolean {
  return NativePhotoVideoEditor.isAvailable();
}
