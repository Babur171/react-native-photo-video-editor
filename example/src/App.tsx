import { useState } from 'react';
import {
  Platform,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  isAvailable,
  openPhotoEditor,
  openVideoEditor,
  PhotoVideoEditorError,
  type EditorResult,
  type MediaType,
} from 'react-native-photo-video-editor';
import { launchImageLibrary, type Asset } from 'react-native-image-picker';
import { ActionButton } from './components/ActionButton';
import { ResultCard } from './components/ResultCard';

export default function App() {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<EditorResult>();
  const [error, setError] = useState<{ code: string; message: string }>();
  const [selectedAsset, setSelectedAsset] = useState<Asset>();

  const selectMedia = async (type: MediaType) => {
    setLoading(true);
    setResult(undefined);
    setError(undefined);
    try {
      const pickerResult = await launchImageLibrary({
        mediaType: type === 'photo' ? 'photo' : 'video',
        selectionLimit: 1,
        includeBase64: false,
      });

      if (pickerResult.didCancel) return;
      if (pickerResult.errorCode) {
        throw new PhotoVideoEditorError(
          pickerResult.errorCode === 'permission'
            ? 'E_PERMISSION_DENIED'
            : 'E_INTERNAL',
          pickerResult.errorMessage ?? 'Unable to select media.'
        );
      }

      const asset = pickerResult.assets?.[0];
      if (!asset?.uri) {
        throw new PhotoVideoEditorError(
          'E_INVALID_URI',
          'The selected media did not provide a usable URI.'
        );
      }

      setSelectedAsset(asset);
    } catch (cause) {
      const value =
        cause instanceof PhotoVideoEditorError
          ? cause
          : new PhotoVideoEditorError('E_INTERNAL', 'Unexpected error.');
      setError({ code: value.code, message: value.message });
    } finally {
      setLoading(false);
    }
  };
  const launchEditor = async () => {
    if (!selectedAsset?.uri) return;
    setLoading(true);
    setResult(undefined);
    setError(undefined);
    const source = {
      uri: selectedAsset.uri,
      mimeType: selectedAsset.type,
      fileName: selectedAsset.fileName,
    };
    try {
      setResult(
        selectedAsset.type?.startsWith('video/')
          ? await openVideoEditor({ source: { ...source, type: 'video' } })
          : await openPhotoEditor({ source: { ...source, type: 'photo' } })
      );
    } catch (cause) {
      const value =
        cause instanceof PhotoVideoEditorError
          ? cause
          : new PhotoVideoEditorError('E_INTERNAL', 'Unexpected error.');
      setError({ code: value.code, message: value.message });
    } finally {
      setLoading(false);
    }
  };
  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text accessibilityRole="header" style={styles.title}>
          Photo Video Editor
        </Text>
        <Text style={styles.subtitle}>
          Select media and open the native Android editor.
        </Text>
        <View style={styles.status}>
          <Text style={styles.statusText}>Platform: {Platform.OS}</Text>
          <Text style={styles.statusText}>
            Native module: {isAvailable() ? 'available' : 'unavailable'}
          </Text>
        </View>
        <ActionButton disabled={loading} onPress={() => selectMedia('photo')}>
          Pick Photo
        </ActionButton>
        <ActionButton disabled={loading} onPress={() => selectMedia('video')}>
          Pick Video
        </ActionButton>
        {selectedAsset?.uri && (
          <ActionButton disabled={loading} onPress={launchEditor}>
            {loading
              ? 'Editor open…'
              : selectedAsset.type?.startsWith('video/')
                ? 'Launch Video Editor'
                : 'Launch Photo Editor'}
          </ActionButton>
        )}
        {selectedAsset?.uri && (
          <View style={styles.selection}>
            <Text style={styles.selectionTitle}>Selected media</Text>
            <Text selectable style={styles.selectionText}>
              {selectedAsset.fileName ?? 'Unnamed file'}
            </Text>
            <Text selectable style={styles.selectionText}>
              {selectedAsset.uri}
            </Text>
          </View>
        )}
        <ResultCard result={result} error={error} />
        {(result || error) && (
          <ActionButton
            secondary
            onPress={() => {
              setResult(undefined);
              setError(undefined);
              setSelectedAsset(undefined);
            }}
          >
            Clear Result
          </ActionButton>
        )}
        <Text style={styles.note}>
          Choose a real image or video. Tap Done in the full-screen editor to
          return the selected media, or Cancel to close it.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f8fafc' },
  container: { gap: 14, padding: 24 },
  title: { color: '#0f172a', fontSize: 28, fontWeight: '800' },
  subtitle: { color: '#475569', fontSize: 16 },
  status: { borderRadius: 10, padding: 16, backgroundColor: '#e0e7ff' },
  statusText: { color: '#1e293b', fontSize: 15 },
  selection: { borderRadius: 10, padding: 16, backgroundColor: '#fef3c7' },
  selectionTitle: { color: '#172033', fontWeight: '700', marginBottom: 6 },
  selectionText: { color: '#334155', marginTop: 4 },
  note: { color: '#475569', lineHeight: 21 },
});
