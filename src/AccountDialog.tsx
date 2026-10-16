import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { call, errorText } from './api';
import { useTheme } from './theme';
import { Button, Dialog, ErrorText, Field, Hint, Label, Row, Saved, s } from './ui';

// 크롬 확장 계정 설정(웹앱 settings-dialog.tsx의 계정 탭)과 같은 요청이다. 매 요청에 확인한 현재 비밀번호를 함께 보낸다.
// 비밀번호가 틀려도 401이 오므로 authed() 대신 call()로 보내 오류 문구만 보여 준다.
const DELETE_ACCOUNT_PHRASE = '계정 삭제';
type Kind = 'tasks' | 'memos' | 'daily-memos';
const DELETE_LABELS: Record<Kind, { title: string; target: string }> = {
  tasks: { title: '업무일지 전체 삭제', target: '모든 업무일지 기록과 측정 시간이' },
  memos: { title: '전체 메모 삭제', target: '모든 메모가' },
  'daily-memos': { title: '전체 일일메모 삭제', target: '모든 날짜의 일일메모가' },
};

type Props = { visible: boolean; onClose: () => void; onDeleted: (kind: Kind) => void; onAccountDeleted: () => void };

export default function AccountDialog({ visible, onClose, onDeleted, onAccountDeleted }: Props) {
  const { c } = useTheme();
  const [password, setPassword] = useState(''); // 확인을 마친 현재 비밀번호(대화상자를 닫으면 지운다)
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [deleting, setDeleting] = useState<Kind | 'account' | null>(null); // 확인 중인 삭제
  const [phrase, setPhrase] = useState('');

  useEffect(() => {
    if (visible) return;
    setPassword('');
    setCurrent('');
    setNext('');
    setConfirm('');
    setError('');
    setMessage('');
    setDeleting(null);
  }, [visible]);

  const request = async (body: Record<string, unknown>, pw = password) => {
    if (busy) return false;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const r = await call('/api/account', { method: 'POST', body: { ...body, password: pw } });
      if (!r.ok) throw Error(r.data.error || '처리하지 못했습니다.');
      return true;
    } catch (e) {
      setError(errorText(e));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const verify = async () => {
    const pw = current;
    setCurrent('');
    if (await request({ action: 'verify' }, pw)) setPassword(pw);
  };
  const changePassword = async () => {
    if (next.length < 8) return setError('비밀번호는 8자 이상 입력해 주세요.');
    if (next !== confirm) return setError('새 비밀번호가 일치하지 않습니다.');
    if (await request({ action: 'change-password', newPassword: next, confirmation: confirm })) {
      setPassword(next);
      setNext('');
      setConfirm('');
      setMessage('비밀번호가 변경되었습니다. 다른 기기는 로그아웃됩니다.');
    }
  };
  const askDelete = (kind: Kind | 'account') => {
    setPhrase('');
    setDeleting(kind);
  };
  // 삭제는 확인 대화상자를 한 번 더 거친다. 계정 삭제는 확인 문구까지 입력해야 한다.
  const doDelete = async () => {
    const kind = deleting;
    setDeleting(null);
    if (!kind) return;
    if (kind === 'account') {
      // 세션 쿠키가 지워졌으므로 로그인 화면으로 돌아간다.
      if (await request({ action: 'delete-account', confirmation: phrase })) onAccountDeleted();
      return;
    }
    if (!(await request({ action: `delete-${kind}` }))) return;
    setMessage(`${DELETE_LABELS[kind].title}가 완료되었습니다.`);
    onDeleted(kind);
  };

  if (!visible) return null;
  if (deleting) {
    const account = deleting === 'account';
    return (
      <Dialog visible onClose={() => setDeleting(null)} title={account ? '계정 삭제' : DELETE_LABELS[deleting].title}>
        <Hint>
          {account
            ? `계정과 모든 업무일지·메모·일일메모가 영구 삭제되며 되돌릴 수 없습니다. 계속하려면 아래에 ‘${DELETE_ACCOUNT_PHRASE}’를 입력해 주세요.`
            : `${DELETE_LABELS[deleting].target} 영구 삭제되며 되돌릴 수 없습니다.`}
        </Hint>
        {account ? (
          <>
            <Label>확인 문구</Label>
            <Field value={phrase} onChangeText={setPhrase} placeholder={DELETE_ACCOUNT_PHRASE} maxLength={20} autoComplete="off" />
          </>
        ) : null}
        <Row>
          <Button title="취소" variant="outline" onPress={() => setDeleting(null)} style={s.flex} />
          <Button
            title={account ? '계정 영구 삭제' : '삭제'}
            variant="dangerFill"
            disabled={account && phrase !== DELETE_ACCOUNT_PHRASE}
            onPress={doDelete}
            style={s.flex}
          />
        </Row>
      </Dialog>
    );
  }
  return (
    <Dialog visible onClose={() => !busy && onClose()} title="계정 설정" closeButton>
      {!password ? (
        <>
          <Hint>계속하려면 현재 비밀번호를 입력해 주세요.</Hint>
          <Label>현재 비밀번호</Label>
          <Field value={current} onChangeText={setCurrent} secureTextEntry maxLength={128} autoComplete="current-password" onSubmitEditing={verify} />
          <Button title="확인" disabled={busy || !current} onPress={verify} />
        </>
      ) : (
        <View style={styles.actions}>
          <View style={styles.group}>
            <Text style={[styles.h3, { color: c.fg }]}>비밀번호 변경</Text>
            <Hint>비밀번호를 바꾸면 다른 기기는 로그아웃됩니다.</Hint>
            <Label>새 비밀번호</Label>
            <Field value={next} onChangeText={setNext} secureTextEntry maxLength={128} autoComplete="new-password" />
            <Label>새 비밀번호 확인</Label>
            <Field value={confirm} onChangeText={setConfirm} secureTextEntry maxLength={128} autoComplete="new-password" />
            <Button title="비밀번호 변경" disabled={busy || !next || !confirm} onPress={changePassword} />
          </View>
          <View style={styles.group}>
            <Text style={[styles.h3, { color: c.fg }]}>데이터 삭제</Text>
            {(Object.keys(DELETE_LABELS) as Kind[]).map((kind) => (
              <Button key={kind} title={DELETE_LABELS[kind].title} variant="danger" disabled={busy} onPress={() => askDelete(kind)} />
            ))}
            <Text style={[styles.h3, { color: c.fg }]}>계정 삭제</Text>
            <Button title="계정 삭제" variant="danger" disabled={busy} onPress={() => askDelete('account')} />
          </View>
        </View>
      )}
      <ErrorText>{error}</ErrorText>
      <Saved>{message}</Saved>
    </Dialog>
  );
}

const styles = StyleSheet.create({
  actions: { gap: 16 },
  group: { gap: 8 },
  h3: { fontSize: 14, fontWeight: '600' },
});
