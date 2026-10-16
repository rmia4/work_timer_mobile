import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { authed, errorText, get } from './api';
import { useTheme } from './theme';
import { Button, Dialog, Hint, Row, s } from './ui';

// 크롬 확장 memo-board.js(웹앱 memo-board.tsx)의 메모 보드. 입력은 0.7초 뒤 자동 저장하고,
// 다른 기기에서 먼저 고쳤으면(409) 자동 저장을 멈추고 어느 쪽을 남길지 고르게 한다.
const SAVE_DELAY = 700;

type Memo = { id: string; title: string; body: string; collapsed?: boolean; version: number };
type Values = { title: string; body: string; collapsed: boolean };
type Failure = Error & { signedOut?: boolean; memo?: Memo };

// 로그아웃(401)이면 authed가 로그인 화면으로 보내므로 signedOut 오류는 화면에 띄우지 않는다.
async function memoRequest(endpoint: string, body: object) {
  const r = await authed(endpoint, { method: 'POST', body });
  if (!r) throw Object.assign(Error(''), { signedOut: true });
  if (!r.ok) throw Object.assign(Error(r.data.error || '메모를 저장하지 못했습니다.'), { memo: r.status === 409 ? r.data.memo : undefined });
  return r.data;
}

const same = (a: Values, b: Values) => a.title === b.title && a.body === b.body && a.collapsed === b.collapsed;

type CardProps = {
  memo: Memo;
  request: (body: object) => Promise<any>;
  collapsible: boolean;
  canUp: boolean;
  canDown: boolean;
  autoFocus: boolean;
  onMove: (direction: -1 | 1) => void;
  onDeleted: () => void;
  confirmRemove: () => Promise<boolean>;
};

function MemoCard({ memo, request, collapsible, canUp, canDown, autoFocus, onMove, onDeleted, confirmRemove }: CardProps) {
  const { c } = useTheme();
  const initial = { title: memo.title, body: memo.body, collapsed: memo.collapsed ?? false };
  const [values, setValues] = useState<Values>(initial);
  const [state, setState] = useState<'saved' | 'saving' | 'error'>('saved');
  const [error, setError] = useState('');
  const [conflict, setConflict] = useState<Memo | null>(null);
  const cur = useRef(initial);
  const persisted = useRef(initial);
  const version = useRef(memo.version);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const saving = useRef(false);
  const queued = useRef(false);
  const conflictRef = useRef<Memo | null>(null);

  const change = (patch: Partial<Values>) => {
    cur.current = { ...cur.current, ...patch };
    setValues(cur.current);
  };
  const fail = (reason: Failure) => {
    if (reason.signedOut) return;
    if (reason.memo) {
      conflictRef.current = reason.memo;
      setConflict(reason.memo);
    }
    setState('error');
    setError(reason.message || '메모를 저장하지 못했습니다.');
  };
  const ok = () => {
    setState('saved');
    setError('');
  };

  const save = useRef(async () => {});
  save.current = async () => {
    if (conflictRef.current) return;
    if (saving.current) {
      queued.current = true;
      return;
    }
    const snapshot = cur.current;
    if (same(snapshot, persisted.current)) return;
    saving.current = true;
    queued.current = false;
    setState('saving');
    try {
      const data = await request({ action: 'update', id: memo.id, version: version.current, ...snapshot });
      if (data.memo) {
        version.current = data.memo.version;
        persisted.current = snapshot;
      }
      ok();
    } catch (reason) {
      fail(reason as Failure);
    } finally {
      saving.current = false;
      if (queued.current && !conflictRef.current && !same(cur.current, snapshot)) save.current();
    }
  };
  const flush = useRef(() => {});
  flush.current = () => {
    if (!timer.current) return;
    clearTimeout(timer.current);
    timer.current = null;
    save.current();
  };
  const schedule = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      timer.current = null;
      save.current();
    }, SAVE_DELAY);
  };
  // 앱이 뒤로 가거나 카드가 사라질 때(날짜 전환) 디바운스 중인 입력을 바로 저장한다.
  useEffect(() => {
    const sub = AppState.addEventListener('change', (next) => next !== 'active' && flush.current());
    return () => {
      sub.remove();
      flush.current();
    };
  }, []);

  const loadServerVersion = () => {
    const server = conflictRef.current;
    if (!server) return;
    const next = { title: server.title, body: server.body, collapsed: server.collapsed ?? false };
    change(next);
    version.current = server.version;
    persisted.current = next;
    conflictRef.current = null;
    setConflict(null);
    ok();
  };
  const keepMine = () => {
    const server = conflictRef.current;
    if (!server) return;
    version.current = server.version;
    persisted.current = { title: server.title, body: server.body, collapsed: server.collapsed ?? false };
    conflictRef.current = null;
    setConflict(null);
    ok();
    save.current();
  };
  const toggleCollapsed = () => {
    change({ collapsed: !cur.current.collapsed });
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    save.current();
  };
  const remove = async () => {
    if ((cur.current.title.trim() || cur.current.body.trim()) && !(await confirmRemove())) return;
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    try {
      await request({ action: 'delete', id: memo.id, version: version.current });
      onDeleted();
    } catch (reason) {
      fail(reason as Failure);
    }
  };

  const icon = (text: string, label: string, onPress: () => void, disabled = false) => (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} disabled={disabled} hitSlop={4} style={[styles.icon, disabled && { opacity: 0.4 }]}>
      <Text style={{ color: c.muted, fontSize: 16 }}>{text}</Text>
    </Pressable>
  );
  return (
    <View style={[styles.card, { backgroundColor: c.bg, borderColor: c.line }]}>
      <View style={styles.head}>
        <TextInput
          accessibilityLabel="메모 제목"
          value={values.title}
          onChangeText={(title) => (change({ title }), schedule())}
          onBlur={() => save.current()}
          placeholder="제목"
          placeholderTextColor={c.muted}
          maxLength={200}
          autoFocus={autoFocus}
          style={[styles.title, { color: c.fg }]}
        />
        <Text style={{ color: c.muted, fontSize: 11, marginRight: 4 }}>{state === 'saving' ? '저장 중' : state === 'error' ? '저장 실패' : '저장됨'}</Text>
        {icon('↑', '위로 이동', () => onMove(-1), !canUp)}
        {icon('↓', '아래로 이동', () => onMove(1), !canDown)}
        {collapsible ? icon(values.collapsed ? '▾' : '▴', values.collapsed ? '메모 펼치기' : '메모 접기', toggleCollapsed) : null}
        {icon('✕', '메모 삭제', remove)}
      </View>
      {values.collapsed ? null : (
        <TextInput
          accessibilityLabel="메모 내용"
          value={values.body}
          onChangeText={(body) => (change({ body }), schedule())}
          onBlur={() => save.current()}
          placeholder="메모를 입력하세요."
          placeholderTextColor={c.muted}
          maxLength={20000}
          multiline
          style={[styles.body, { color: c.fg, borderColor: c.line }]}
        />
      )}
      {error ? (
        <View style={styles.error}>
          <Text style={{ color: c.danger, fontSize: 13 }}>{error}</Text>
          <Row>
            {conflict ? (
              <>
                <Button title="최신 내용 불러오기" variant="outline" small onPress={loadServerVersion} />
                <Button title="내 내용으로 덮어쓰기" variant="outline" small onPress={keepMine} />
              </>
            ) : (
              <Button title="다시 저장" variant="outline" small onPress={() => save.current()} />
            )}
          </Row>
        </View>
      ) : null}
    </View>
  );
}

// endpoint: /api/memos(메모장) 또는 /api/daily-memos(일일 메모, day를 준다).
export default function MemoBoard({ endpoint, day, collapsible = false, emptyText }: { endpoint: string; day?: string; collapsible?: boolean; emptyText: string }) {
  const { c } = useTheme();
  const [memos, setMemos] = useState<Memo[] | null>(null); // null: 불러오는 중
  const [generation, setGeneration] = useState(0); // 다시 불러오면 카드를 새로 만든다
  const [error, setError] = useState('');
  const [moving, setMoving] = useState(false);
  const [focusId, setFocusId] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<((ok: boolean) => void) | null>(null);
  const request = useCallback((body: object) => memoRequest(endpoint, day ? { ...body, day } : body), [endpoint, day]);

  const reload = useCallback(async () => {
    setMemos(null);
    setError('');
    try {
      const data = await get(day ? `${endpoint}?day=${day}` : endpoint);
      if (!data) return;
      setMemos(data.memos);
      setGeneration((g) => g + 1);
    } catch (e) {
      setMemos([]);
      setError(errorText(e, '메모를 불러오지 못했습니다.'));
    }
  }, [endpoint, day]);
  useEffect(() => {
    reload();
  }, [reload]);

  const create = async () => {
    setError('');
    try {
      const data = await request({ action: 'create' });
      if (!data.memo) return;
      setMemos((list) => [...(list || []), data.memo]);
      setFocusId(data.memo.id);
    } catch (e) {
      if (!(e as Failure).signedOut) setError(errorText(e));
    }
  };

  const move = async (index: number, direction: -1 | 1) => {
    if (moving || !memos) return;
    const swapIndex = index + direction;
    if (swapIndex < 0 || swapIndex >= memos.length) return;
    const card = memos[index],
      swap = memos[swapIndex];
    const next = [...memos];
    next[index] = swap;
    next[swapIndex] = card;
    setMemos(next);
    setMoving(true);
    setError('');
    try {
      await request({ action: 'reorder', id: card.id, swapId: swap.id });
    } catch (e) {
      // 실패하면 서버 순서로 다시 불러온 뒤 오류를 보여 준다.
      if (!(e as Failure).signedOut) {
        await reload();
        setError(errorText(e));
      }
    }
    setMoving(false);
  };

  // 웹앱의 window.confirm 대신 앱 안 대화상자로 묻는다(뒤로 가기·취소면 false).
  const confirmRemove = () => new Promise<boolean>((resolve) => setConfirm(() => resolve));
  const answer = (ok: boolean) => {
    confirm?.(ok);
    setConfirm(null);
  };

  return (
    <View>
      {error ? (
        <View style={styles.error}>
          <Text style={{ color: c.danger, fontSize: 13 }}>{error}</Text>
          <Button title="다시 불러오기" variant="outline" small onPress={reload} />
        </View>
      ) : null}
      {memos === null ? (
        <Hint>메모를 불러오는 중입니다.</Hint>
      ) : !memos.length ? (
        <View style={styles.empty}>
          <Hint>{emptyText}</Hint>
          <Button title="+ 첫 메모 만들기" variant="outline" small onPress={create} style={{ marginTop: 8 }} />
        </View>
      ) : (
        <>
          {memos.map((m, i) => (
            <MemoCard
              key={`${generation}:${m.id}`}
              memo={m}
              request={request}
              collapsible={collapsible}
              canUp={!moving && i > 0}
              canDown={!moving && i < memos.length - 1}
              autoFocus={m.id === focusId}
              onMove={(d) => move(i, d)}
              onDeleted={() => setMemos((list) => (list || []).filter((x) => x.id !== m.id))}
              confirmRemove={confirmRemove}
            />
          ))}
          <Button title="+ 새 메모" variant="outline" small onPress={create} style={{ alignSelf: 'flex-end' }} />
        </>
      )}
      <Dialog visible={!!confirm} onClose={() => answer(false)} title="이 메모를 삭제하시겠습니까?">
        <Row>
          <Button title="취소" variant="outline" onPress={() => answer(false)} style={s.flex} />
          <Button title="삭제" onPress={() => answer(true)} style={s.flex} />
        </Row>
      </Dialog>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { marginBottom: 8, paddingHorizontal: 10, paddingTop: 4, paddingBottom: 10, borderWidth: 1, borderRadius: 8 },
  head: { flexDirection: 'row', alignItems: 'center' },
  title: { flex: 1, minHeight: 40, fontSize: 15, fontWeight: '600', paddingVertical: 4 },
  icon: { minWidth: 30, minHeight: 36, alignItems: 'center', justifyContent: 'center' },
  body: { minHeight: 60, marginTop: 4, padding: 8, borderWidth: 1, borderRadius: 6, fontSize: 15, textAlignVertical: 'top' },
  error: { gap: 6, marginVertical: 6 },
  empty: { paddingVertical: 24, alignItems: 'center' },
});
