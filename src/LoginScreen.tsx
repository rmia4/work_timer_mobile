import { useState } from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';
import { call, errorText } from './api';
import { useTheme } from './theme';
import { Button, ErrorText, Field, Hint, Label, ModeTabs } from './ui';

// 크롬 확장(웹앱 login.tsx)의 로그인·회원가입 화면과 같은 규칙이다.
export default function LoginScreen({ onSignedIn }: { onSignedIn: () => Promise<boolean> }) {
  const { c } = useTheme();
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const signup = mode === 'signup';

  const changeMode = (next: 'login' | 'signup') => {
    if (next === mode) return;
    setMode(next);
    setPassword('');
    setConfirmation('');
    setError('');
  };

  const submit = async () => {
    if (busy) return;
    if (!username || !password || (signup && !confirmation)) return setError('아이디와 비밀번호를 입력해 주세요.');
    setBusy(true);
    setError('');
    try {
      const r = await call(signup ? '/api/auth/signup' : '/api/auth', { method: 'POST', body: { username, password, confirmation } });
      if (!r.ok) throw Error(r.data.error || (signup ? '가입하지 못했습니다.' : '로그인하지 못했습니다.'));
      setPassword('');
      setConfirmation('');
      // 로그인은 됐는데 바로 401이면 기기가 로그인 쿠키를 저장하지 않은 것이다.
      if (!(await onSignedIn())) setError('로그인 정보를 저장하지 못했습니다. 다시 시도해 주세요.');
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView style={{ backgroundColor: c.page }} contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Text style={[styles.title, { color: c.fg }]}>Work Timer</Text>
      <ModeTabs value={mode} onChange={changeMode} options={[['login', '로그인'], ['signup', '회원가입']]} />
      <Hint>
        {signup ? '아이디는 영문 소문자·숫자·밑줄 4~20자, 비밀번호는 8자 이상 입력해 주세요.' : '아이디와 비밀번호를 입력해 주세요.'}
      </Hint>
      <Label>아이디</Label>
      <Field value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} maxLength={20} autoComplete="username" textContentType="username" />
      <Label>비밀번호</Label>
      <Field
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        maxLength={128}
        autoComplete={signup ? 'new-password' : 'current-password'}
        onSubmitEditing={signup ? undefined : submit}
      />
      {signup ? (
        <>
          <Label>비밀번호 확인</Label>
          <Field value={confirmation} onChangeText={setConfirmation} secureTextEntry maxLength={128} autoComplete="new-password" onSubmitEditing={submit} />
        </>
      ) : null}
      <Button title={signup ? '가입하기' : '로그인'} onPress={submit} disabled={busy} style={styles.submit} />
      <ErrorText>{error}</ErrorText>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, justifyContent: 'center', padding: 24, gap: 8 },
  title: { fontSize: 26, fontWeight: '700', textAlign: 'center', marginBottom: 16 },
  submit: { marginTop: 8 },
});
