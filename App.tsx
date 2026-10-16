import { StatusBar } from 'expo-status-bar';
import * as WebBrowser from 'expo-web-browser';
import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { isSignedIn, onSignedOut } from './src/auth';
import LoginScreen from './src/LoginScreen';
import TimerScreen from './src/TimerScreen';

WebBrowser.maybeCompleteAuthSession();

export default function App() {
  const [state, setState] = useState<'loading' | 'signedOut' | 'signedIn'>('loading');
  useEffect(() => {
    onSignedOut(() => setState('signedOut'));
    isSignedIn().then((signedIn) => setState(signedIn ? 'signedIn' : 'signedOut'));
  }, []);
  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.container}>
        {state === 'loading' ? (
          <ActivityIndicator style={styles.container} />
        ) : state === 'signedOut' ? (
          <LoginScreen onSignedIn={() => setState('signedIn')} />
        ) : (
          <TimerScreen onSignedOut={() => setState('signedOut')} />
        )}
        <StatusBar style="dark" />
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f3f4f6' },
});
