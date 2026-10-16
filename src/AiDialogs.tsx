import type { ReactNode } from 'react';
import * as Clipboard from 'expo-clipboard';
import { useEffect, useState } from 'react';
import { Linking, StyleSheet, Text, View } from 'react-native';
import { authed, errorText } from './api';
import { ORIGIN } from './config';
import { useTheme } from './theme';
import { Button, Dialog, ErrorText, Hint, Row, Saved, s } from './ui';

// 크롬 확장의 AI 연결 메뉴(웹앱 workspace-parts.tsx)와 연결된 앱 목록(settings-dialog.tsx). 고른 항목의 대화상자로 바꿔 연다.
type Kind = 'menu' | 'claude' | 'codex' | 'connections';
type Connection = { id: string; name: string; host?: string; scopes: string[]; created: number; lastUsed: number | null };

const pad = (n: number) => String(n).padStart(2, '0');
const connectionTime = (t: number) => {
  const d = new Date(t + 9 * 3600000);
  return `${d.getUTCFullYear()}. ${d.getUTCMonth() + 1}. ${d.getUTCDate()}. ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
};

export default function AiDialogs({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const [kind, setKind] = useState<Kind>('menu');
  useEffect(() => {
    if (visible) setKind('menu');
  }, [visible]);
  if (!visible) return null;
  if (kind === 'claude') return <ClaudeDialog onClose={onClose} />;
  if (kind === 'codex') return <CodexDialog onClose={onClose} />;
  if (kind === 'connections') return <ConnectionsDialog onClose={onClose} />;
  return (
    <Dialog visible onClose={onClose} title="AI 연결" closeButton>
      <Button title="Claude에 연결" variant="outline" onPress={() => setKind('claude')} />
      <Button title="Codex에 연결" variant="outline" onPress={() => setKind('codex')} />
      <Button title="연결된 앱" variant="outline" onPress={() => setKind('connections')} />
    </Dialog>
  );
}

// claude.ai의 커스텀 커넥터 추가 화면을 이름·MCP 주소를 채운 채로 브라우저에서 연다.
function ClaudeDialog({ onClose }: { onClose: () => void }) {
  const open = () => {
    const params = `modal=add-custom-connector&connectorName=${encodeURIComponent('Work Timer')}&connectorUrl=${encodeURIComponent(`${ORIGIN}/api/mcp`)}`;
    Linking.openURL(`https://claude.ai/customize/connectors?${params}`);
  };
  return (
    <Dialog visible onClose={onClose} title="Claude에 연결" closeButton>
      <Hint>claude.ai의 커스텀 커넥터 추가 화면을 Work Timer 주소를 채운 채로 엽니다. 연결을 추가한 뒤 Work Timer 로그인·동의 화면이 열리면 허용합니다.</Hint>
      <Button title="claude.ai 열기" onPress={open} />
    </Dialog>
  );
}

function CodexDialog({ onClose }: { onClose: () => void }) {
  const { c } = useTheme();
  const [error, setError] = useState('');
  const [copied, setCopied] = useState('');
  // Codex 앱 딥링크. 이 기기에 Codex 앱이 없으면 열리지 않는다.
  const open = (url: string) => {
    setError('');
    Linking.openURL(url).catch(() => setError('Codex 앱을 열지 못했습니다. Codex 앱이 설치된 기기에서 진행해 주세요.'));
  };
  // Codex "마켓플레이스 추가"에 입력할 값을 복사한다.
  const copy = async (value: string) => {
    setError('');
    try {
      await Clipboard.setStringAsync(value);
      setCopied(value);
      setTimeout(() => setCopied(''), 2000);
    } catch {
      setError('복사하지 못했습니다. 값을 직접 입력해 주세요.');
    }
  };
  const step = (n: number, children: ReactNode) => (
    <View style={styles.step}>
      <Text style={{ color: c.fg, fontSize: 13 }}>{n}.</Text>
      <View style={[s.flex, { gap: 6 }]}>{children}</View>
    </View>
  );
  const field = (label: string, value: string) => (
    <View style={styles.field}>
      <Text style={{ color: c.muted, fontSize: 13, minWidth: 52 }}>{label}</Text>
      <Text style={[styles.code, { color: c.fg, borderColor: c.line, backgroundColor: c.page }]}>{value}</Text>
      <Button title={copied === value ? '복사했습니다' : '복사'} variant="outline" small onPress={() => copy(value)} />
    </View>
  );
  const text = (t: ReactNode) => <Text style={{ color: c.fg, fontSize: 13, lineHeight: 19 }}>{t}</Text>;
  return (
    <Dialog visible onClose={onClose} title="Codex에 연결" closeButton>
      <Hint>Codex 앱에 Work Timer 마켓플레이스를 추가한 뒤 플러그인을 설치합니다.</Hint>
      {step(
        1,
        <>
          {text('Codex 앱에서 플러그인 화면을 엽니다.')}
          <Button title="Codex 플러그인 화면 열기" variant="outline" small onPress={() => open('codex://plugins')} />
        </>,
      )}
      {step(
        2,
        <>
        {text(
          <>
            <Text style={{ fontWeight: '700' }}>관리 &gt; 마켓플레이스</Text> 탭에서 <Text style={{ fontWeight: '700' }}>마켓플레이스 추가</Text>를 누르고 아래 값을 입력합니다. Sparse 경로는 비워 둡니다.
          </>,
        )}
        <View style={{ gap: 6 }}>
          {field('출처', 'rmia4/work_timer')}
          {field('Git ref', 'plugin/codex')}
        </View>
        </>,
      )}
      {step(
        3,
        <>
          {text('Work Timer 플러그인을 열어 설치하고, Work Timer 로그인·동의 화면이 열리면 허용합니다.')}
          <Button title="Work Timer 플러그인 열기" small onPress={() => open('codex://plugins/install/work-timer?marketplace=work-timer-local')} />
        </>,
      )}
      <ErrorText>{error}</ErrorText>
    </Dialog>
  );
}

function ConnectionsDialog({ onClose }: { onClose: () => void }) {
  const { c } = useTheme();
  const [list, setList] = useState<Connection[] | null>(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [target, setTarget] = useState<Connection | null>(null); // 해제를 확인 중인 연결

  useEffect(() => {
    (async () => {
      try {
        const r = await authed('/api/connections');
        if (!r) return;
        if (!r.ok || !r.data.connections) throw Error(r.data.error || '연결된 앱을 불러오지 못했습니다.');
        setList(r.data.connections);
      } catch (e) {
        setList([]);
        setError(errorText(e, '연결된 앱을 불러오지 못했습니다.'));
      }
    })();
  }, []);

  const disconnect = async () => {
    const t = target;
    setTarget(null);
    if (!t || busy) return;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const r = await authed('/api/connections', { method: 'POST', body: { action: 'disconnect', id: t.id } });
      if (!r) return;
      // 이미 해제된 연결(404)도 목록에서 빼면 된다.
      if (!r.ok && r.status !== 404) throw Error(r.data.error || '처리하지 못했습니다.');
      setList((l) => (l || []).filter((x) => x.id !== t.id));
      setMessage(`${t.name} 연결을 해제했습니다.`);
    } catch (e) {
      setError(errorText(e, '처리하지 못했습니다.'));
    } finally {
      setBusy(false);
    }
  };

  if (target)
    return (
      <Dialog visible onClose={() => setTarget(null)} title="연결 해제">
        <Hint>{`${target.name} 연결을 해제할까요? 해제하면 그 앱은 더 이상 업무 기록에 접근할 수 없으며, 다시 쓰려면 새로 연결해야 합니다.`}</Hint>
        <Row>
          <Button title="취소" variant="outline" onPress={() => setTarget(null)} style={s.flex} />
          <Button title="연결 해제" variant="dangerFill" onPress={disconnect} style={s.flex} />
        </Row>
      </Dialog>
    );
  return (
    <Dialog visible onClose={onClose} title="연결된 앱" closeButton>
      {list === null ? <Hint>불러오는 중…</Hint> : !list.length && !error ? <Hint>연결된 앱이 없습니다.</Hint> : null}
      {(list || []).map((x) => (
        <View key={x.id} style={[styles.connection, { borderColor: c.line }]}>
          <View style={[s.flex, { gap: 2 }]}>
            <Text style={{ color: c.fg, fontWeight: '700', fontSize: 13 }}>{x.name}</Text>
            {[x.host, `권한: ${x.scopes.join(', ')}`, `연결 ${connectionTime(x.created)} · 마지막 사용 ${x.lastUsed ? connectionTime(x.lastUsed) : '없음'}`]
              .filter(Boolean)
              .map((line) => (
                <Text key={line} style={{ color: c.muted, fontSize: 12 }}>
                  {line}
                </Text>
              ))}
          </View>
          <Button title="연결 해제" variant="danger" small disabled={busy} onPress={() => setTarget(x)} />
        </View>
      ))}
      <ErrorText>{error}</ErrorText>
      <Saved>{message}</Saved>
    </Dialog>
  );
}

const styles = StyleSheet.create({
  step: { flexDirection: 'row', gap: 6 },
  field: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  code: { paddingVertical: 2, paddingHorizontal: 6, borderWidth: 1, borderRadius: 4, fontFamily: 'monospace', fontSize: 13 },
  connection: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 8, borderWidth: 1, borderRadius: 6 },
});
