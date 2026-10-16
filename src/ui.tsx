import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View, type StyleProp, type TextInputProps, type TextStyle, type ViewStyle } from 'react-native';
import { useTheme } from './theme';
// 크롬 확장 popup.css의 버튼·입력·대화상자 모양을 옮긴 공통 컴포넌트.

type Variant = 'primary' | 'outline' | 'danger' | 'dangerFill' | 'ghost';

export function Button({
  title,
  onPress,
  variant = 'primary',
  disabled,
  small,
  style,
  textStyle,
  label,
}: {
  title: string;
  onPress: () => void;
  variant?: Variant;
  disabled?: boolean;
  small?: boolean;
  style?: StyleProp<ViewStyle>;
  textStyle?: StyleProp<TextStyle>;
  label?: string;
}) {
  const { c } = useTheme();
  const look = {
    primary: [c.accent, c.accent, c.bg],
    outline: [c.bg, c.line, c.fg],
    danger: [c.bg, c.danger, c.danger],
    dangerFill: [c.danger, c.danger, c.bg],
    ghost: ['transparent', 'transparent', c.muted],
  }[variant];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        s.button,
        small && s.small,
        { backgroundColor: look[0], borderColor: look[1] },
        (disabled || pressed) && { opacity: disabled ? 0.5 : 0.7 },
        style,
      ]}
    >
      <Text style={[s.buttonText, small && s.smallText, { color: look[2] }, textStyle]}>{title}</Text>
    </Pressable>
  );
}

export function Field(props: TextInputProps) {
  const { c } = useTheme();
  return (
    <TextInput
      placeholderTextColor={c.muted}
      {...props}
      style={[s.input, { borderColor: c.line, color: c.fg }, props.multiline && s.multiline, props.style]}
    />
  );
}

export function Label({ children }: { children: ReactNode }) {
  const { c } = useTheme();
  return <Text style={[s.label, { color: c.muted }]}>{children}</Text>;
}

export function Hint({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  const { c } = useTheme();
  return <Text style={[s.hint, { color: c.muted }, style]}>{children}</Text>;
}

// 빈 문구면 그리지 않는다(확장의 .error:empty).
export function ErrorText({ children }: { children: string }) {
  const { c } = useTheme();
  return children ? (
    <Text accessibilityRole="alert" style={[s.hint, { color: c.danger }]}>
      {children}
    </Text>
  ) : null;
}

export function Saved({ children }: { children: string }) {
  const { c } = useTheme();
  return children ? <Text style={[s.hint, { color: c.accent }]}>{children}</Text> : null;
}

export function Row({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[s.row, style]}>{children}</View>;
}

// 확장 .mode: 테두리 버튼 두 개 중 고른 쪽을 강조한다.
export function ModeTabs<T extends string>({ value, options, onChange }: { value: T; options: [T, string][]; onChange: (v: T) => void }) {
  const { c } = useTheme();
  return (
    <Row>
      {options.map(([v, text]) => {
        const on = v === value;
        return (
          <Pressable
            key={v}
            accessibilityRole="tab"
            accessibilityState={{ selected: on }}
            onPress={() => onChange(v)}
            style={[s.button, s.flex, { borderColor: on ? c.accent : c.line }]}
          >
            <Text style={[s.buttonText, { color: on ? c.accent : c.muted, fontWeight: on ? '600' : '400' }]}>{text}</Text>
          </Pressable>
        );
      })}
    </Row>
  );
}

// 확장 .tabs: 밑줄 탭.
export function UnderlineTabs<T extends string>({ value, options, onChange }: { value: T; options: [T, string][]; onChange: (v: T) => void }) {
  const { c } = useTheme();
  return (
    <View style={[s.tabs, { borderBottomColor: c.line }]}>
      {options.map(([v, text]) => {
        const on = v === value;
        return (
          <Pressable
            key={v}
            accessibilityRole="tab"
            accessibilityState={{ selected: on }}
            onPress={() => onChange(v)}
            style={[s.tab, { borderBottomColor: on ? c.accent : 'transparent' }]}
          >
            <Text style={{ color: on ? c.accent : c.muted, fontWeight: on ? '600' : '400', fontSize: 15 }}>{text}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

// 확장의 <dialog>. 바깥(배경)을 누르거나 뒤로 가기로 닫는다(onClose가 없으면 닫히지 않는다).
export function Dialog({ visible, onClose, title, children, closeButton }: { visible: boolean; onClose?: () => void; title?: string; children: ReactNode; closeButton?: boolean }) {
  const { c } = useTheme();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={() => onClose?.()}>
      <KeyboardAvoidingView behavior="padding" style={s.backdrop}>
        <Pressable style={StyleSheet.absoluteFill} onPress={() => onClose?.()} accessibilityLabel="닫기" />
        <View style={[s.dialog, { backgroundColor: c.bg, borderColor: c.line }]}>
          <ScrollView contentContainerStyle={s.dialogBody} keyboardShouldPersistTaps="handled">
            {title ? <Text style={[s.dialogTitle, { color: c.fg }]}>{title}</Text> : null}
            {children}
          </ScrollView>
          {closeButton && onClose ? (
            <Pressable accessibilityRole="button" accessibilityLabel="닫기" onPress={onClose} style={s.close} hitSlop={8}>
              <Text style={{ color: c.muted, fontSize: 22 }}>×</Text>
            </Pressable>
          ) : null}
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const { c } = useTheme();
  return <View style={[s.card, { backgroundColor: c.bg, borderColor: c.line }, style]}>{children}</View>;
}

export const s = StyleSheet.create({
  flex: { flex: 1 },
  row: { flexDirection: 'row', gap: 8 },
  button: { minHeight: 44, paddingHorizontal: 12, borderWidth: 1, borderRadius: 6, alignItems: 'center', justifyContent: 'center' },
  buttonText: { fontSize: 15 },
  small: { minHeight: 34, paddingHorizontal: 10 },
  smallText: { fontSize: 13 },
  input: { minHeight: 44, paddingHorizontal: 10, borderWidth: 1, borderRadius: 6, fontSize: 15 },
  multiline: { minHeight: 110, paddingVertical: 8, textAlignVertical: 'top' },
  label: { fontSize: 13 },
  hint: { fontSize: 13, lineHeight: 18 },
  tabs: { flexDirection: 'row', borderBottomWidth: 1, marginBottom: 8 },
  tab: { flex: 1, minHeight: 40, alignItems: 'center', justifyContent: 'center', borderBottomWidth: 2, marginBottom: -1 },
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'center', padding: 20 },
  dialog: { maxHeight: '90%', borderWidth: 1, borderRadius: 8 },
  dialogBody: { padding: 16, gap: 8 },
  dialogTitle: { fontSize: 16, fontWeight: '600', marginBottom: 4, marginRight: 28 },
  close: { position: 'absolute', top: 6, right: 10, width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  card: { borderWidth: 1, borderRadius: 8, padding: 12 },
  tabular: { fontVariant: ['tabular-nums'] },
});
