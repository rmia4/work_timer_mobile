import { useCallback, useEffect, useState } from 'react';
import { Alert, AppState, KeyboardAvoidingView, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SignedOutError, signOut } from './auth';
import { STATUS_LABEL, clock, duration } from './format';
import { finishWork, getCurrentWork, listTodayTasks, pauseWork, resumeWork, startWork, type Task } from './mcp';
import { styles as common } from './styles';

// 진행 중 작업(시작·일시정지·재개·종료)과 오늘 기록. 조작한 뒤에는 항상 서버에서 다시 읽는다.
export default function TimerScreen({ onSignedOut }: { onSignedOut: () => void }) {
  const [current, setCurrent] = useState<Task | null>(null);
  const [today, setToday] = useState<Task[]>([]);
  const [fetchedAt, setFetchedAt] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [title, setTitle] = useState('');
  const [target, setTarget] = useState('');
  const [finishing, setFinishing] = useState(false);
  const [summary, setSummary] = useState('');

  const showError = (e: unknown) => {
    if (!(e instanceof SignedOutError)) Alert.alert('오류', (e as Error).message);
  };
  const load = useCallback(async () => {
    try {
      const [task, list] = await Promise.all([getCurrentWork(), listTodayTasks()]);
      setCurrent(task);
      setToday(list.tasks);
      setFetchedAt(Date.now());
    } catch (e) {
      showError(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const sub = AppState.addEventListener('change', (s) => s === 'active' && load());
    return () => sub.remove();
  }, [load]);
  // 서버는 분 단위로만 주므로, 진행 중이면 받은 뒤 흐른 시간을 더해 보여 준다.
  useEffect(() => {
    if (current?.status !== 'running') return;
    const timer = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(timer);
  }, [current?.status]);

  const act = async (action: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await action();
    } catch (e) {
      showError(e);
    }
    await load();
    setBusy(false);
  };
  const start = () => act(async () => {
    await startWork(title.trim(), Number(target) || undefined);
    setTitle('');
    setTarget('');
  });
  const finish = () => act(async () => {
    await finishWork(summary.trim());
    setFinishing(false);
    setSummary('');
  });
  const logout = () =>
    Alert.alert('로그아웃', '이 기기의 연결을 해제합니다.', [
      { text: '취소', style: 'cancel' },
      { text: '로그아웃', style: 'destructive', onPress: () => signOut().then(onSignedOut) },
    ]);

  const elapsed = current ? current.minutes + (current.status === 'running' ? Math.floor(Math.max(0, now - fetchedAt) / 60000) : 0) : 0;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Work Timer</Text>
        <Pressable onPress={logout} hitSlop={8}>
          <Text style={styles.link}>로그아웃</Text>
        </Pressable>
      </View>
      <ScrollView contentContainerStyle={styles.content} refreshControl={<RefreshControl refreshing={loading} onRefresh={load} />} keyboardShouldPersistTaps="handled">
        {current ? (
          <View style={styles.card}>
            <Text style={styles.status}>{STATUS_LABEL[current.status]}</Text>
            <Text style={styles.taskTitle}>{current.title}</Text>
            <Text style={styles.elapsed}>{duration(elapsed)}</Text>
            {current.target_minutes ? <Text style={styles.muted}>목표 {duration(current.target_minutes)}</Text> : null}
            <View style={styles.row}>
              {current.status === 'running' ? (
                <Pressable style={[common.button, common.secondary, styles.flex]} disabled={busy} onPress={() => act(pauseWork)}>
                  <Text style={common.secondaryText}>일시정지</Text>
                </Pressable>
              ) : (
                <Pressable style={[common.button, common.secondary, styles.flex]} disabled={busy} onPress={() => act(resumeWork)}>
                  <Text style={common.secondaryText}>재개</Text>
                </Pressable>
              )}
              <Pressable style={[common.button, common.primary, styles.flex]} disabled={busy} onPress={() => setFinishing(true)}>
                <Text style={common.primaryText}>종료</Text>
              </Pressable>
            </View>
          </View>
        ) : loading ? null : (
          <View style={styles.card}>
            <Text style={styles.status}>새 작업</Text>
            <TextInput style={common.input} placeholder="작업 제목" value={title} onChangeText={setTitle} maxLength={200} />
            <TextInput style={common.input} placeholder="목표 시간(분, 선택)" value={target} onChangeText={(v) => setTarget(v.replace(/\D/g, ''))} keyboardType="number-pad" maxLength={6} />
            <Pressable style={[common.button, common.primary]} disabled={busy || !title.trim()} onPress={start}>
              <Text style={common.primaryText}>시작</Text>
            </Pressable>
          </View>
        )}

        <Text style={styles.section}>
          오늘 기록 · {duration(today.reduce((sum, t) => sum + (t.id === current?.id ? elapsed : t.minutes), 0))}
        </Text>
        {today.length === 0 && !loading ? <Text style={styles.muted}>오늘 기록이 없습니다.</Text> : null}
        {today.map((t) => (
          <View key={t.id} style={styles.item}>
            <View style={styles.flex}>
              <Text style={styles.itemTitle}>{t.title}</Text>
              <Text style={styles.muted}>
                {STATUS_LABEL[t.status]}
                {t.started_at ? ` · ${clock(t.started_at)}~${clock(t.ended_at)}` : ''}
              </Text>
            </View>
            <Text style={styles.itemMinutes}>{duration(t.id === current?.id ? elapsed : t.minutes)}</Text>
          </View>
        ))}
      </ScrollView>

      <Modal visible={finishing} transparent animationType="fade" onRequestClose={() => setFinishing(false)}>
        <KeyboardAvoidingView behavior="padding" style={styles.backdrop}>
          <View style={styles.dialog}>
            <Text style={styles.taskTitle}>작업 종료</Text>
            <TextInput style={[common.input, styles.summary]} placeholder="결과 요약" value={summary} onChangeText={setSummary} multiline maxLength={20000} autoFocus />
            <View style={styles.row}>
              <Pressable style={[common.button, common.secondary, styles.flex]} onPress={() => setFinishing(false)}>
                <Text style={common.secondaryText}>취소</Text>
              </Pressable>
              <Pressable style={[common.button, common.primary, styles.flex]} disabled={busy || !summary.trim()} onPress={finish}>
                <Text style={common.primaryText}>종료</Text>
              </Pressable>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f3f4f6' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 12 },
  headerTitle: { fontSize: 20, fontWeight: '700' },
  link: { color: '#2563eb', fontSize: 15 },
  content: { padding: 16, gap: 12 },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 16, gap: 10 },
  status: { fontSize: 13, color: '#2563eb', fontWeight: '600' },
  taskTitle: { fontSize: 18, fontWeight: '600' },
  elapsed: { fontSize: 36, fontWeight: '700' },
  muted: { fontSize: 13, color: '#6b7280' },
  row: { flexDirection: 'row', gap: 8 },
  flex: { flex: 1 },
  section: { fontSize: 15, fontWeight: '600', marginTop: 8 },
  item: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#fff', borderRadius: 10, padding: 12, gap: 8 },
  itemTitle: { fontSize: 15 },
  itemMinutes: { fontSize: 15, fontWeight: '600' },
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'center', padding: 24 },
  dialog: { backgroundColor: '#fff', borderRadius: 12, padding: 16, gap: 12 },
  summary: { minHeight: 100, textAlignVertical: 'top' },
});
