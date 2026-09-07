import type { CodegenTypes, TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  openEditor(request: string): Promise<string>;
  cancelExport(jobId: string | null): Promise<boolean>;
  isAvailable(): boolean;
  readonly onExportProgress: CodegenTypes.EventEmitter<string>;
  readonly onEditorEvent: CodegenTypes.EventEmitter<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PhotoVideoEditor');
