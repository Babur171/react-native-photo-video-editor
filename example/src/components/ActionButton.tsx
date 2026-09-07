import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
export function ActionButton({
  children,
  onPress,
  disabled = false,
  secondary = false,
}: {
  children: ReactNode;
  onPress: () => void;
  disabled?: boolean;
  secondary?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={String(children)}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        secondary && styles.secondary,
        (pressed || disabled) && styles.dim,
      ]}
    >
      <Text style={[styles.text, secondary && styles.secondaryText]}>
        {children}
      </Text>
    </Pressable>
  );
}
const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 10,
    paddingHorizontal: 18,
    backgroundColor: '#2563eb',
  },
  secondary: { backgroundColor: '#e2e8f0' },
  dim: { opacity: 0.55 },
  text: { color: '#fff', fontSize: 16, fontWeight: '700' },
  secondaryText: { color: '#172033' },
});
