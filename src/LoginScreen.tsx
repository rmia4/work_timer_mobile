import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { signIn } from './auth';
import { styles as common } from './styles';

export default function LoginScreen({ onSignedIn }: { onSignedIn: () => void }) {
  const [busy, setBusy] = useState(false);
  const login = async () => {
    setBusy(true);
    try {
      if (await signIn()) onSignedIn();
    } catch (e) {
      Alert.alert('로그인 실패', (e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Work Timer</Text>
      <Text style={styles.text}>웹의 Work Timer 계정으로 로그인하면 타이머를 조작하고 오늘 기록을 볼 수 있습니다.</Text>
      <Pressable style={[common.button, common.primary]} onPress={login} disabled={busy}>
        {busy ? <ActivityIndicator color="#fff" /> : <Text style={common.primaryText}>로그인</Text>}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24, gap: 16 },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center' },
  text: { fontSize: 15, color: '#555', textAlign: 'center', lineHeight: 22 },
});
