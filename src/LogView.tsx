import type { ReactNode } from 'react';
import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import Calendar from './Calendar';
import { duration, shiftDay, statusLabel, timeLabel, today, weekday, type Task } from './dates';
import MemoBoard from './MemoBoard';
import { useTheme } from './theme';
import { Elapsed } from './TimerView';
import { Button, Card, Dialog, ErrorText, Hint, Row, UnderlineTabs, s } from './ui';

type Props = {
  day: string;
  loading: boolean;
  tasks: Task[];
  busy: boolean;
  now: () => number;
  calendarVersion: number;
  memoReset: number;
  onChooseDay: (day: string) => void;
  onEdit: (t: Task) => void;
  onRemove: (t: Task) => void;
  error: string;
};

// 크롬 확장 일지 화면: 날짜를 옮기거나 캘린더에서 고른 날짜의 기록과 일일 메모를 본다.
export default function LogView({ day, loading, tasks, busy, now, calendarVersion, memoReset, onChooseDay, onEdit, onRemove, error }: Props) {
  const { c } = useTheme();
  const [mode, setMode] = useState<'records' | 'memos'>('records');
  const [calendar, setCalendar] = useState(false);
  const [remove, setRemove] = useState<Task | null>(null); // 삭제 확인 중인 기록

  return (
    <ScrollView contentContainerStyle={styles.main} keyboardShouldPersistTaps="handled">
      <UnderlineTabs value={mode} onChange={setMode} options={[['records', '업무 일지'], ['memos', '일일 메모']]} />
      <View style={styles.dateNav}>
        <Button title="‹" label="이전 날" variant="outline" small disabled={busy} onPress={() => onChooseDay(shiftDay(day, -1))} />
        <Text style={[s.tabular, { color: c.fg, fontSize: 14, paddingHorizontal: 4 }]}>
          {day} ({weekday(day)})
        </Text>
        <Button title="›" label="다음 날" variant="outline" small disabled={busy} onPress={() => onChooseDay(shiftDay(day, 1))} />
        <Button title="오늘" variant="outline" small disabled={busy} onPress={() => onChooseDay(today())} style={{ marginLeft: 'auto' }} />
        <Button title="캘린더" label="캘린더 열기" variant="outline" small onPress={() => setCalendar(true)} />
      </View>

      {mode === 'records' ? (
        loading ? (
          <Hint>기록을 불러오는 중입니다.</Hint>
        ) : tasks.length ? (
          tasks.map((t) => <Record key={t.id} t={t} now={now} busy={busy} onEdit={() => onEdit(t)} onRemove={() => setRemove(t)} />)
        ) : (
          <View style={styles.empty}>
            <Text style={{ color: c.fg, fontWeight: '600' }}>아직 기록이 없습니다.</Text>
            <Hint style={{ marginTop: 4 }}>작업을 시작하거나 완료한 업무를 남겨보세요.</Hint>
          </View>
        )
      ) : (
        <MemoBoard key={`${memoReset}:${day}`} endpoint="/api/daily-memos" day={day} emptyText="선택한 날짜에 메모를 남겨보세요." />
      )}
      <ErrorText>{error}</ErrorText>

      <Dialog visible={calendar} onClose={() => setCalendar(false)}>
        <Calendar
          day={day}
          version={calendarVersion}
          onChoose={(value) => {
            onChooseDay(value);
            setCalendar(false);
          }}
        />
        <Button title="닫기" variant="outline" onPress={() => setCalendar(false)} />
      </Dialog>

      <Dialog visible={!!remove} onClose={() => setRemove(null)} title="이 기록을 삭제하시겠습니까?">
        <Hint>“{remove?.title}”의 업무 내용과 측정 시간이 삭제됩니다.</Hint>
        <Row>
          <Button title="취소" variant="outline" onPress={() => setRemove(null)} style={s.flex} />
          <Button
            title="삭제"
            onPress={() => {
              const t = remove;
              setRemove(null);
              if (t) onRemove(t);
            }}
            style={s.flex}
          />
        </Row>
      </Dialog>
    </ScrollView>
  );
}

// 웹앱 RecordList(카드형)와 같은 내용이다. 소요 시간만 1초마다 다시 그린다.
function Record({ t, now, busy, onEdit, onRemove }: { t: Task; now: () => number; busy: boolean; onEdit: () => void; onRemove: () => void }) {
  const { c } = useTheme();
  const time = (label: string, value: ReactNode) => (
    <View>
      <Text style={{ color: c.muted, fontSize: 11 }}>{label}</Text>
      {value}
    </View>
  );
  const strong = [s.tabular, { color: c.fg, fontWeight: '700' as const, fontSize: 14 }];
  return (
    <Card style={styles.record}>
      <View style={styles.recordHead}>
        <Text style={[styles.recordTitle, { color: c.fg }]}>{t.title}</Text>
        <View style={styles.times}>
          {t.target > 0 ? time('목표', <Text style={strong}>{duration(t.target)}</Text>) : null}
          {time('소요', <Elapsed task={t} now={now} style={strong} />)}
        </View>
      </View>
      <Text style={{ color: c.muted, fontSize: 13, marginVertical: 4 }}>
        {statusLabel(t.status)} · 시작 {timeLabel(t.started_at)} · 종료 {t.status === 'done' ? timeLabel(t.ended_at) : '진행 중'}
      </Text>
      {(
        [
          ['업무 내용', t.note, false],
          ['업무 결과', t.result, true],
        ] as const
      ).map(([label, text, isResult]) =>
        text ? (
          <View key={label} style={[styles.journal, { borderLeftColor: isResult ? c.accent : c.line }]}>
            <Text style={{ color: isResult ? c.accent : c.muted, fontSize: 13, fontWeight: '600' }}>{label}</Text>
            <Text style={{ color: c.fg, fontSize: 14, marginTop: 2 }}>{text}</Text>
          </View>
        ) : null,
      )}
      {t.status === 'done' ? (
        <Row style={{ marginTop: 8 }}>
          <Button title="수정" variant="outline" small disabled={busy} onPress={onEdit} style={s.flex} />
          <Button title="삭제" variant="outline" small disabled={busy} onPress={onRemove} style={s.flex} />
        </Row>
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  main: { padding: 16, paddingTop: 8, gap: 8 },
  dateNav: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  empty: { paddingVertical: 24, alignItems: 'center' },
  record: { paddingVertical: 10 },
  recordHead: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  recordTitle: { flex: 1, fontSize: 15, fontWeight: '600' },
  times: { flexDirection: 'row', gap: 10 },
  journal: { marginTop: 8, paddingLeft: 8, paddingVertical: 2, borderLeftWidth: 3 },
});
