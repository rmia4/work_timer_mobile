import * as SecureStore from 'expo-secure-store';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Linking, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import AccountDialog from './AccountDialog';
import AiDialogs from './AiDialogs';
import { authed, call, errorText } from './api';
import { invalidateCalendarCache } from './Calendar';
import { ORIGIN } from './config';
import { today, type Task } from './dates';
import LogView from './LogView';
import MemoBoard from './MemoBoard';
import SummaryView from './SummaryView';
import { useTheme } from './theme';
import TimerView from './TimerView';
import { ErrorText } from './ui';

type Screen = 'timer' | 'log' | 'memo' | 'summary';
const VIEWS: [Screen, string][] = [
  ['timer', '타이머'],
  ['log', '일지'],
  ['memo', '메모'],
  ['summary', '요약'],
];
const VIEW_KEY = 'view'; // 마지막으로 연 화면(앱을 다시 열면 그대로 보여 준다)

// 크롬 확장 popup.js의 상태와 동작이다. 화면은 하단 탭으로 바꾸고, 한 번 연 화면은 숨겨 둘 뿐 입력 내용을 유지한다.
export default function MainScreen({ onRenew, onSignedOut }: { onRenew: () => void; onSignedOut: () => void }) {
  const { c, toggle } = useTheme();
  const insets = useSafeAreaInsets();
  const [view, setViewState] = useState<Screen>('timer');
  const [visited, setVisited] = useState<Set<Screen>>(new Set<Screen>(['timer']));
  const [day, setDay] = useState(today); // 일지에서 보는 날짜
  const [loading, setLoading] = useState(true); // 보고 있는 날짜의 기록을 아직 못 받았는지
  const [tasks, setTasks] = useState<Task[]>([]);
  const [offset, setOffset] = useState(0); // 서버 시각 - 기기 시각
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [edit, setEdit] = useState<Task | null>(null); // 수정 중인 기록
  const [formKey, setFormKey] = useState(0); // 바꾸면 작업 폼을 비운다
  const [calendarVersion, setCalendarVersion] = useState(0); // 저장 뒤 캘린더를 다시 불러오게 한다
  const [menu, setMenu] = useState(false);
  const [ai, setAi] = useState(false);
  const [account, setAccount] = useState(false);
  const [memoReset, setMemoReset] = useState(0); // 데이터 삭제 뒤 메모 보드를 다시 불러온다
  const request = useRef(0); // 늦게 온 이전 조회 응답을 버리기 위한 번호
  const dayRef = useRef(day);
  dayRef.current = day;
  const tasksRef = useRef(tasks);
  tasksRef.current = tasks;
  const busyRef = useRef(false);

  const task = tasks.find((t) => t.status !== 'done') || null;

  const setView = (v: Screen) => {
    setViewState(v);
    setVisited((prev) => (prev.has(v) ? prev : new Set(prev).add(v)));
    SecureStore.setItemAsync(VIEW_KEY, v).catch(() => {});
  };
  useEffect(() => {
    SecureStore.getItemAsync(VIEW_KEY)
      .then((v) => VIEWS.some(([k]) => k === v) && setView(v as Screen))
      .catch(() => {});
  }, []);

  const load = useCallback(async () => {
    const seq = ++request.current;
    try {
      const r = await authed(`/api/tasks?day=${dayRef.current}`);
      if (seq !== request.current || !r) return;
      if (!r.ok) throw Error(r.data.error);
      // 이미 보여 주고 있는 기록과 같으면 다시 그리지 않는다(10초 갱신 때 화면을 새로 만들지 않도록).
      if (JSON.stringify(r.data.tasks) !== JSON.stringify(tasksRef.current)) setTasks(r.data.tasks);
      setOffset(r.data.now - Date.now());
      setLoading(false);
      setError('');
    } catch (e) {
      if (seq === request.current) setError(errorText(e));
    }
  }, []);

  // 앱을 열 때·앞으로 돌아올 때 세션을 늘리고 다시 불러온다. 10초마다 다시 불러와 다른 기기의 변경을 반영한다.
  useEffect(() => {
    load();
    const sub = AppState.addEventListener('change', (state) => {
      if (state !== 'active') return;
      onRenew();
      load();
    });
    const timer = setInterval(() => {
      if (AppState.currentState === 'active' && !busyRef.current) load();
    }, 10000);
    return () => {
      sub.remove();
      clearInterval(timer);
    };
  }, [load, onRenew]);

  // 웹앱 use-workspace.ts의 act와 같다. 409면 최신 상태를 다시 불러온다. 실패하면 오류 문구를 돌려준다.
  const act = useCallback(
    async (body: Record<string, unknown>): Promise<string | null> => {
      if (busyRef.current) return '';
      busyRef.current = true;
      setBusy(true);
      setError('');
      setMessage('');
      try {
        const r = await authed('/api/tasks', { method: 'POST', body });
        if (!r) return '';
        if (!r.ok) {
          if (r.status === 409) await load();
          throw Error(r.data.error);
        }
        await load();
        invalidateCalendarCache(dayRef.current);
        if (typeof body.day === 'string') invalidateCalendarCache(body.day);
        setCalendarVersion((v) => v + 1);
        setMessage('저장되었습니다.');
        return null;
      } catch (e) {
        return errorText(e, '저장에 실패했습니다.');
      } finally {
        busyRef.current = false;
        setBusy(false);
      }
    },
    [load],
  );
  // 화면 아래에 오류를 보여 주는 act.
  const actShow = async (body: Record<string, unknown>) => {
    const failed = await act(body);
    if (failed) setError(failed);
    return failed === null;
  };

  const clearForm = () => {
    setEdit(null);
    setFormKey((k) => k + 1);
  };

  // 웹앱 chooseDay(): 다른 날짜를 고르면 그 날짜 기록을 새로 불러온다.
  const chooseDay = (value: string) => {
    if (value === dayRef.current) return;
    request.current++;
    dayRef.current = value;
    setDay(value);
    setLoading(true);
    setTasks([]);
    if (edit) clearForm();
    setMessage('');
    load();
  };

  // 웹앱 startEdit(): 완료 기록을 타이머 화면의 폼에 채운다.
  const startEdit = (t: Task) => {
    setEdit(t);
    setFormKey((k) => k + 1);
    setMessage('');
    setView('timer');
  };

  const logout = async () => {
    setMenu(false);
    setError('');
    try {
      const r = await call('/api/auth', { method: 'DELETE' });
      if (!r.ok) throw Error('로그아웃하지 못했습니다.');
      onSignedOut();
    } catch (e) {
      setError(errorText(e));
    }
  };

  // 업무일지·메모를 지웠으면 화면에서도 비운다.
  const onDeleted = (kind: 'tasks' | 'memos' | 'daily-memos') => {
    if (kind === 'tasks') {
      invalidateCalendarCache();
      setCalendarVersion((v) => v + 1);
      if (edit) clearForm();
      load();
    } else setMemoReset((n) => n + 1);
  };

  const shown = (v: Screen) => (view === v ? styles.flex : styles.hidden);
  const now = () => Date.now() + offset;

  return (
    <View style={[styles.flex, { backgroundColor: c.page }]}>
      <View style={[styles.nav, { backgroundColor: c.bg, borderBottomColor: c.line }]}>
        <Text accessibilityRole="header" style={[styles.title, { color: c.fg }]}>{VIEWS.find(([v]) => v === view)?.[1]}</Text>
        <Pressable accessibilityRole="button" accessibilityLabel="설정" onPress={() => setMenu(true)} style={styles.gear} hitSlop={8}>
          <Svg width={20} height={20} viewBox="0 0 24 24" fill="none" stroke={c.muted} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
            <Circle cx={12} cy={12} r={3} />
            <Path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.830 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h0a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h0a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v0a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
          </Svg>
        </Pressable>
      </View>

      <View style={shown('timer')}>
        <ScrollView contentContainerStyle={styles.main} keyboardShouldPersistTaps="handled">
          <TimerView
            key={formKey}
            task={task}
            edit={edit}
            day={day}
            busy={busy}
            message={message}
            now={now}
            act={act}
            actShow={actShow}
            onCancelEdit={clearForm}
            onSaved={clearForm}
          />
          <ErrorText>{error}</ErrorText>
        </ScrollView>
      </View>
      {visited.has('log') ? (
        <View style={shown('log')}>
          <LogView
            day={day}
            loading={loading}
            tasks={tasks.filter((t) => t.day === day)}
            busy={busy}
            now={now}
            calendarVersion={calendarVersion}
            memoReset={memoReset}
            onChooseDay={chooseDay}
            onEdit={startEdit}
            onRemove={(t) => actShow({ action: 'delete', id: t.id, version: t.version })}
            error={error}
          />
        </View>
      ) : null}
      {visited.has('memo') ? (
        <View style={shown('memo')}>
          <ScrollView contentContainerStyle={styles.main} keyboardShouldPersistTaps="handled">
            <MemoBoard key={memoReset} endpoint="/api/memos" collapsible emptyText="항상 표시할 메모를 남겨보세요." />
          </ScrollView>
        </View>
      ) : null}
      {visited.has('summary') ? (
        <View style={shown('summary')}>
          <SummaryView day={day} tasks={tasks} now={now} active={view === 'summary'} />
        </View>
      ) : null}

      <View style={[styles.tabs, { backgroundColor: c.bg, borderTopColor: c.line }]}>
        {VIEWS.map(([v, text], i) => (
          <Pressable key={v} accessibilityRole="tab" accessibilityState={{ selected: view === v }} onPress={() => setView(v)} style={styles.tab}>
            {i > 0 ? <View style={[styles.navLine, { borderLeftColor: c.line }]} /> : null}
            <Text style={{ fontSize: 15, color: view === v ? c.fg : c.muted, fontWeight: view === v ? '600' : '400' }}>{text}</Text>
          </Pressable>
        ))}
      </View>

      {/* 설정 버튼 아래에 붙는 메뉴. 바깥을 누르거나 뒤로 가기로 닫힌다. */}
      <Modal visible={menu} transparent animationType="fade" onRequestClose={() => setMenu(false)}>
        <Pressable style={styles.flex} onPress={() => setMenu(false)}>
          <View style={[styles.menu, { top: insets.top + 52, backgroundColor: c.bg, borderColor: c.line }]}>
            {[
              [`${c.dark ? '라이트 모드' : '다크 모드'}로 전환`, () => (toggle(), setMenu(false))],
              ['웹페이지 방문', () => (setMenu(false), Linking.openURL(ORIGIN))],
              ['AI 연결', () => (setMenu(false), setAi(true))],
              null,
              ['계정 설정', () => (setMenu(false), setAccount(true))],
              ['로그아웃', logout],
            ].map((item, i) =>
              item ? (
                <Pressable
                  key={i}
                  accessibilityRole="menuitem"
                  onPress={item[1] as () => void}
                  disabled={busy && item[0] === '로그아웃'}
                  style={({ pressed }) => [styles.menuItem, pressed && { backgroundColor: c.page }]}
                >
                  <Text style={{ color: c.fg, fontSize: 15 }}>{item[0] as string}</Text>
                </Pressable>
              ) : (
                <View key={i} style={[styles.menuLine, { borderTopColor: c.line }]} />
              ),
            )}
          </View>
        </Pressable>
      </Modal>
      <AiDialogs visible={ai} onClose={() => setAi(false)} />
      <AccountDialog visible={account} onClose={() => setAccount(false)} onDeleted={onDeleted} onAccountDeleted={onSignedOut} />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  hidden: { display: 'none' },
  main: { padding: 16, gap: 8 },
  nav: { flexDirection: 'row', alignItems: 'center', minHeight: 48, paddingHorizontal: 8, borderBottomWidth: 1 },
  title: { paddingHorizontal: 8, fontSize: 17, fontWeight: '600' },
  tabs: { flexDirection: 'row', borderTopWidth: 1 },
  tab: { flex: 1, minHeight: 52, alignItems: 'center', justifyContent: 'center' },
  navLine: { position: 'absolute', left: 0, top: 19, height: 14, borderLeftWidth: 1 },
  gear: { marginLeft: 'auto', padding: 10 },
  menu: { position: 'absolute', right: 8, minWidth: 180, padding: 4, borderWidth: 1, borderRadius: 8, elevation: 6 },
  menuItem: { minHeight: 44, paddingHorizontal: 12, justifyContent: 'center', borderRadius: 4 },
  menuLine: { marginVertical: 4, borderTopWidth: 1 },
});
