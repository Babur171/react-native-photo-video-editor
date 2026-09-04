/** Kind of local media supplied to the editor. */
export type MediaType = 'photo' | 'video';
/** Requested export quality preset. */
export type ExportQuality = 'low' | 'medium' | 'high' | 'original';
/** Supported photo output container. */
export type ImageExportFormat = 'jpeg' | 'png' | 'webp';
/** Supported video output container. */
export type VideoExportFormat = 'mp4' | 'mov';
/** Lifecycle state reported by future export jobs. */
export type EditorStatus =
  | 'idle'
  | 'preparing'
  | 'editing'
  | 'exporting'
  | 'completed'
  | 'cancelled'
  | 'failed';

/** A local file or platform content URI. Remote URLs are not supported. */
export interface EditorSource {
  /** Local file/content URI. */ uri: string;
  /** Media kind. */ type: MediaType;
  /** Optional MIME type hint. */ mimeType?: string;
  /** Optional display filename. */ fileName?: string;
}
/** Feature switches. Video-only switches are normalized off for photos. */
export interface EditorFeatures {
  /** Enables cropping. */ crop?: boolean;
  /** Enables rotation. */ rotate?: boolean;
  /** Enables video trimming. */ trim?: boolean;
  /** Enables filters. */ filters?: boolean;
  /** Enables text overlays. */ text?: boolean;
  /** Enables stickers (built-in, consumer-supplied, and user-uploaded). */ stickers?: boolean;
  /** Enables the user-uploaded image overlay tool. */ overlays?: boolean;
  /** Enables browsing/downloading free stickers from the internet (OpenMoji). Disable to prevent any network calls. */ onlineStickers?: boolean;
}
/** Future native editor appearance options. */
export interface EditorTheme {
  /** CSS-style primary color. */ primaryColor?: string;
  /** Editor background color. */ backgroundColor?: string;
  /** Toolbar color. */ toolbarColor?: string;
  /** Foreground text color. */ textColor?: string;
  /** Status bar content style. */ statusBarStyle?: 'light' | 'dark';
}
/** Export preferences. The milestone-one placeholder does not export media. */
export interface ExportOptions {
  /** Quality preset. */ quality?: ExportQuality;
  /** Photo output format; photo sources only. */ imageFormat?: ImageExportFormat;
  /** Video output format; video sources only. */ videoFormat?: VideoExportFormat;
  /** Maximum output width in pixels; must be positive. */ maxWidth?: number;
  /** Maximum output height in pixels; must be positive. */ maxHeight?: number;
  /** Video bitrate in bits per second; must be positive. */ videoBitrate?: number;
  /** Video frame rate in frames per second; must be positive. */ frameRate?: number;
  /** Whether future exports should retain safe metadata. */ preserveMetadata?: boolean;
}
/** A consumer-provided font for text layers. Licensing is the consumer's responsibility. */
export interface FontAsset {
  /** Font family name used to select this font from a text layer. */ family: string;
  /** Local file URI of the font file (e.g. .ttf/.otf). */ uri: string;
}
/** A consumer-provided sticker image, selectable from the Stickers tool. */
export interface StickerAsset {
  /** Stable identifier, echoed back on layers created from this asset. */ id: string;
  /** Local file/content URI of the sticker image. */ uri: string;
}
/** Options accepted by {@link openEditor}. */
export interface EditorOptions {
  /** Required local input. */ source: EditorSource;
  /** Enabled tools. */ features?: EditorFeatures;
  /** Native UI theme. */ theme?: EditorTheme;
  /** Export preferences. */ export?: ExportOptions;
  /** Requests saving to the gallery. Currently ignored by the placeholder. */ saveToGallery?: boolean;
  /** Consumer-provided stickers offered by the Stickers tool, in addition to the small built-in set and end-user uploads. */ stickerAssets?: StickerAsset[];
  /** Consumer-provided fonts selectable from the Text tool. */ fonts?: FontAsset[];
}
export interface PhotoEditorOptions extends Omit<EditorOptions, 'source'> {
  source: EditorSource & { type: 'photo' };
}
export interface VideoEditorOptions extends Omit<EditorOptions, 'source'> {
  source: EditorSource & { type: 'video' };
}
export type EditorEventType =
  | 'editorOpened'
  | 'editorReady'
  | 'selectionChanged'
  | 'projectChanged'
  | 'exportStarted'
  | 'exportProgress'
  | 'exportCompleted'
  | 'exportCancelled'
  | 'editorClosed'
  | 'error';
export interface EditorEvent {
  type: EditorEventType;
  projectId?: string;
  jobId?: string;
  progress?: number;
  status?: EditorStatus;
}
export type EditorEventListener = (event: EditorEvent) => void;
/** Result from the native editor contract. */
export interface EditorResult {
  /** Local output URI. */ uri: string;
  /** Output media kind. */ type: MediaType;
  /** MIME type when known. */ mimeType?: string;
  /** Width in pixels when known. */ width?: number;
  /** Height in pixels when known. */ height?: number;
  /** Video duration in milliseconds when known. */ duration?: number;
  /** File size in bytes when known. */ fileSize?: number;
  /** True only when editing was cancelled. */ cancelled: boolean;
}
/** Export progress. Progress is between 0 and 1. */
export interface ExportProgressEvent {
  /** Export job identifier. */ jobId: string;
  /** Fraction complete, from 0 to 1. */ progress: number;
  /** Current lifecycle state. */ status: EditorStatus;
}
/** Removable event subscription. */
export interface EditorSubscription {
  /** Stops future callbacks; safe to call repeatedly. */ remove(): void;
}
