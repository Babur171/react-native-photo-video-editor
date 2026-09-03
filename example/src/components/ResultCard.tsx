import { StyleSheet, Text, View } from 'react-native';
import type { EditorResult } from 'react-native-photo-video-editor';
export function ResultCard({
  result,
  error,
}: {
  result?: EditorResult;
  error?: { code: string; message: string };
}) {
  if (!result && !error) return null;
  return (
    <View
      accessibilityRole="summary"
      style={[styles.card, error && styles.error]}
    >
      <Text style={styles.heading}>
        {error ? `Error: ${error.code}` : 'Native result'}
      </Text>
      <Text selectable style={styles.body}>
        {error ? error.message : JSON.stringify(result, null, 2)}
      </Text>
    </View>
  );
}
const styles = StyleSheet.create({
  card: { padding: 16, borderRadius: 10, backgroundColor: '#dcfce7' },
  error: { backgroundColor: '#fee2e2' },
  heading: { color: '#172033', fontWeight: '700', marginBottom: 8 },
  body: { color: '#273349', fontFamily: 'monospace' },
});
