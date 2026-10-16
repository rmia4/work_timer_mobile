import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { duration, elapsedOf, parseTimeInput, statusLabel, timeInput, timestampLabel, today, type Task } from './dates';
import { useTheme } from './theme';
import { Button, Dialog, ErrorText, Field, Hint, Label, ModeTabs, Row, Saved, s } from './ui';

// 1초(또는 주어진 간격)마다 다시 그리게 한다.
export function useTick(ms = 1000) {
  const [, setTick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => setTick((n) => n + 1), ms);
    return () => clearInterval(timer);
  }, [ms]);
}

// 기록 중인 작업의 경과 시간. 이 부분만 1초마다 다시 그린다.
export function Elapsed({ task, now, style }: { task: Task; now: () => number; style: object }) {
  useTick();
  return <Text style={style}>{duration(elapsedOf(task, now()))}</Text>;
}

const minutesOk = (v: string) => v === '' || (Number.isFinite(Number(v)) && Number(v) >= 0 && Number(v) <= 525600);

type Props = {
  task: Task | null;
  edit: Task | null;
  day: string;
  busy: boolean;
  message: string;
  now: () => number;
  act: (body: Record<string, unknown>) => Promise<string | null>;
  actShow: (body: Record<string, unknown>) => Promise<boolean>;
  onCancelEdit: () => void;
  onSaved: () => void;
};

// 크롬 확장 타이머 화면(웹앱 TimerPanel·TaskForm)과 같은 문구와 버튼 규칙이다.
// 기록 중인 작업이 있으면 그 작업만, 없으면 새 기록 추가 폼만 보여 준다. 기록 수정 중에는 폼만 보여 준다.
export default function TimerView({ task, edit, day, busy, message, now, act, actShow, onCancelEdit, onSaved }: Props) {
  const { c } = useTheme();
  const [title, setTitle] = useState(edit?.title ?? '');
  const [note, setNote] = useState(edit?.note ?? '');
  const [result, setResult] = useState(edit?.result ?? '');
  const [work, setWork] = useState<'note' | 'result'>('note');
  const [target, setTarget] = useState(edit && edit.target > 0 ? String(Math.round(edit.target / 600) / 100) : '');
  const [minutes, setMinutes] = useState(edit ? String(Math.round(edit.elapsed / 600) / 100) : '0');
  const [manual, setManual] = useState(false); // 소요 시간 직접 입력 펼침
  const [formError, setFormError] = useState('');
  const [finishTask, setFinishTask] = useState<Task | null>(null); // 종료·저장 대화상자를 열 때의 진행 작업
  const [finishResult, setFinishResult] = useState('');
  const [finishError, setFinishError] = useState('');

  // 웹앱 save(): 목표·소요 시간은 분 단위 입력을 밀리초로 바꾼다.
  const save = async (run: boolean) => {
    if (!title.trim()) return setFormError('작업명을 입력해 주세요.');
    if (!minutesOk(target) || !minutesOk(minutes) || minutes === '') return setFormError('시간은 0~525600분으로 입력해 주세요.');
    setFormError('');
    const ok = await actShow({
      action: edit ? 'edit' : 'create',
      id: edit?.id,
      version: edit?.version,
      title,
      note,
      result,
      target: target === '' ? 0 : Math.round(Number(target) * 60000),
      // 바로 측정을 시작하면 보고 있는 날짜와 상관없이 오늘에 기록한다.
      day: run && !edit ? today() : day,
      elapsed: Math.round(Number(minutes) * 60000),
      started_at: edit ? parseTimeInput(timeInput(edit.started_at)) : null,
      ended_at: edit ? parseTimeInput(timeInput(edit.ended_at)) : null,
      run,
    });
    if (ok) onSaved();
  };

  const openFinish = () => {
    if (!task) return;
    setFinishTask(task);
    setFinishResult(task.result || '');
    setFinishError('');
  };
  const closeFinish = () => !busy && setFinishTask(null);
  const finish = async () => {
    if (!finishTask) return;
    const failed = await act({ action: 'finish', id: finishTask.id, version: finishTask.version, result: finishResult });
    if (failed === null) setFinishTask(null);
    else setFinishError(failed);
  };

  const showActive = !!task && !edit;
  return (
    <>
      {showActive ? (
        <>
          <View style={[styles.active, { backgroundColor: c.active }]}>
            <Text style={[styles.state, { color: c.activeMuted }]}>작업 스톱워치 · {statusLabel(task.status)}</Text>
            <Text style={[styles.title, { color: c.activeText }]}>{task.title}</Text>
            {task.target > 0 ? <Text style={[styles.state, { color: c.activeMuted }]}>목표 {duration(task.target)}</Text> : null}
            <Elapsed task={task} now={now} style={[styles.clock, s.tabular, { color: c.activeText }]} />
            <Hint style={{ color: c.activeMuted, marginBottom: 12 }}>{timestampLabel(task.started_at)} 시작 · 앱을 닫아도 기록이 유지됩니다.</Hint>
            <Row>
              <Button
                title={task.status === 'running' ? '일시정지' : '다시 시작'}
                disabled={busy}
                onPress={() => actShow({ action: task.status === 'running' ? 'pause' : 'resume', id: task.id, version: task.version })}
                style={[s.flex, { backgroundColor: c.toggleBg, borderColor: c.toggleLine }]}
                textStyle={{ color: c.toggleText }}
              />
              <Button
                title="종료·저장"
                disabled={busy}
                onPress={openFinish}
                style={[s.flex, { backgroundColor: 'transparent', borderColor: c.activeOutline }]}
                textStyle={{ color: '#fff' }}
              />
            </Row>
          </View>
          {task.note ? (
            <View style={[styles.journal, { backgroundColor: c.bg, borderLeftColor: c.line }]}>
              <Text style={[styles.journalLabel, { color: c.muted }]}>업무 내용</Text>
              <Text style={[styles.note, { color: c.fg }]}>{task.note}</Text>
            </View>
          ) : null}
        </>
      ) : (
        <View style={[styles.form, { backgroundColor: c.bg, borderColor: c.line }]}>
          <Text style={[styles.formTitle, { color: c.fg }]}>{edit ? '기록 수정' : '새 기록 추가'}</Text>
          <Label>작업명</Label>
          <Field value={title} onChangeText={setTitle} maxLength={200} placeholder="목표 작업 적기" />
          <ModeTabs value={work} onChange={setWork} options={[['note', '업무 내용'], ['result', '업무 결과']]} />
          {work === 'note' ? (
            <Field accessibilityLabel="업무 내용" value={note} onChangeText={setNote} multiline maxLength={20000} placeholder="작업 세부사항, 목표 적기" />
          ) : (
            <Field accessibilityLabel="업무 결과" value={result} onChangeText={setResult} multiline maxLength={20000} placeholder="결과, 산출물, 추후 확인 사항 적기" />
          )}
          <Label>목표 시간 (분)</Label>
          <Field value={target} onChangeText={setTarget} keyboardType="decimal-pad" placeholder="선택 입력" />
          <Hint>입력하지 않으면 목표 시간이 표시되지 않습니다.</Hint>
          <Pressable accessibilityRole="button" accessibilityState={{ expanded: manual }} onPress={() => setManual(!manual)} hitSlop={6}>
            <Text style={{ color: c.muted, fontSize: 13 }}>{manual ? '▾' : '▸'} 소요 시간 직접 입력 (선택)</Text>
          </Pressable>
          {manual ? (
            <>
              <Label>소요 시간 (분)</Label>
              <Field value={minutes} onChangeText={setMinutes} keyboardType="decimal-pad" />
              <Hint>직접 기록할 때 입력하세요. 측정 시작 시에는 0부터 시작합니다.</Hint>
            </>
          ) : null}
          <Row>
            <Button title={edit ? '수정 후 측정 재개' : '측정 시작'} disabled={busy || !!task || !title.trim()} onPress={() => save(true)} style={s.flex} />
            <Button title={edit ? '수정 저장' : '기록 저장'} variant="outline" disabled={busy} onPress={() => save(false)} style={s.flex} />
            {edit ? <Button title="취소" variant="outline" onPress={onCancelEdit} style={s.flex} /> : null}
          </Row>
          <ErrorText>{formError}</ErrorText>
          <Saved>{message}</Saved>
        </View>
      )}

      <Dialog visible={!!finishTask} onClose={closeFinish} title="업무 결과 저장">
        <Hint>“{finishTask?.title}” 작업을 종료합니다. 완료한 결과를 기록해 주세요.</Hint>
        <Label>업무 결과</Label>
        <Field value={finishResult} onChangeText={setFinishResult} multiline maxLength={20000} placeholder="완료 결과, 산출물, 확인 사항을 입력하세요." style={{ minHeight: 160 }} />
        <Hint style={{ textAlign: 'right' }}>{finishResult.length.toLocaleString()} / 20,000</Hint>
        <ErrorText>{finishError}</ErrorText>
        <Row>
          <Button title="취소" variant="outline" disabled={busy} onPress={closeFinish} style={s.flex} />
          <Button title={busy ? '저장 중…' : '종료하고 저장'} disabled={busy} onPress={finish} style={s.flex} />
        </Row>
      </Dialog>
    </>
  );
}

const styles = StyleSheet.create({
  active: { padding: 16, borderRadius: 8 },
  state: { fontSize: 13 },
  title: { fontSize: 18, fontWeight: '600', marginVertical: 4 },
  clock: { fontSize: 40, marginTop: 8, marginBottom: 12 },
  journal: { marginTop: 4, paddingVertical: 8, paddingHorizontal: 10, borderLeftWidth: 3, borderRadius: 8, borderTopLeftRadius: 0, borderBottomLeftRadius: 0 },
  journalLabel: { fontSize: 13, fontWeight: '600' },
  note: { fontSize: 15, marginTop: 2 },
  form: { padding: 16, borderWidth: 1, borderRadius: 8, gap: 8 },
  formTitle: { fontSize: 16, fontWeight: '600' },
});
