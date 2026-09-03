import type { EditorSource, ExportOptions } from './types';
export const EDITOR_PROJECT_SCHEMA_VERSION = 1 as const;
export interface EditorProject {
  schemaVersion: typeof EDITOR_PROJECT_SCHEMA_VERSION;
  id: string;
  source: EditorSource;
  canvas: {
    width: number;
    height: number;
    backgroundColor: string;
    durationMs?: number;
  };
  tracks: Array<{ id: string; items: string[] }>;
  adjustments: Record<string, number>;
  exportDefaults: ExportOptions;
  metadata: { createdAt: string; updatedAt: string };
}
