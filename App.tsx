import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { call, onUnauthorized, renewSession } from './src/api';
import LoginScreen from './src/LoginScreen';
import MainScreen from './src/MainScreen';
import { ThemeProvider, useTheme } from './src/theme';

function Root() {
  const { c } = useTheme();
  const [state, setState] = useState<'loading' | 'signedOut' | 'signedIn'>('loading');
  // 로그인 쿠키가 아직 유효한지 확인한다. 유효하면 세션도 늘린다.
  const check = useCallback(async () => {
    const r = await call('/api/auth', { method: 'PATCH' }).catch(() => null);
    // 연결이 안 되면 일단 화면을 열고 오류는 각 화면이 보여 준다.
    const signedIn = r ? r.status !== 401 : true;
    setState(signedIn ? 'signedIn' : 'signedOut');
    return signedIn;
  }, []);
  useEffect(() => {
    onUnauthorized(() => setState('signedOut'));
    check();
  }, [check]);
  return (
    <SafeAreaView style={[styles.container, { backgroundColor: c.bg }]}>
      {state === 'loading' ? (
        <ActivityIndicator style={styles.container} color={c.accent} />
      ) : state === 'signedOut' ? (
        <LoginScreen onSignedIn={check} />
      ) : (
        <MainScreen onRenew={renewSession} onSignedOut={() => setState('signedOut')} />
      )}
      <StatusBar style={c.dark ? 'light' : 'dark'} />
    </SafeAreaView>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <Root />
      </ThemeProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
});
