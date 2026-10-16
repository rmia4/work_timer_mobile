import * as Clipboard from 'expo-clipboard';
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import ActivityClock, { activitySegments } from './ActivityClock';
import { download, errorText } from './api';
import { duration, today, type Task } from './dates';
import { summaryPrompt } from './exportPrompt';
import { useTheme } from './theme';
import { useTick } from './TimerView';
import { Button, Dialog, ErrorText, Field, Hint, Label, Row, s } from './ui';

// 크롬 확장 요약 화면: 일지에서 고른 날짜의 측정 시간과 하루 활동 시계. 시계는 1분마다 다시 그린다.
export default function SummaryView({ day, tasks, now, active }: { day: string; tasks: Task[]; now: () => number; active: boolean }) {
  const { c } = useTheme();
  useTick(active ? 60000 : 3600000);
  const [csv, setCsv] = useState(false);
  const at = now();
  const segments = activitySegments(tasks, day, at);
  const list = tasks.filter((t) => t.day === day);
  return (
    <ScrollView contentContainerStyle={styles.main}>
      <View style={styles.head}>
        <Text style={[styles.total, s.tabular, { color: c.fg }]}>{duration(segments.reduce((sum, x) => sum + x.end - x.start, 0))}</Text>
        <Button title="CSV" variant="outline" small onPress={() => setCsv(true)} />
      </View>
      <Text style={{ color: c.muted, fontSize: 13 }}>
        {day === today() ? '오늘' : day} 측정 시간 · 작업 {list.length}개 · 완료 {list.filter((t) => t.status === 'done').length}개
      </Text>
      <ActivityClock segments={segments} now={at} />
      {csv ? <CsvDialog day={day} onClose={() => setCsv(false)} /> : null}
    </ScrollView>
  );
}

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const dayCount = (from: string, to: string) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000) + 1;
const csvInvalid = (from: string, to: string) =>
  !DATE.test(from) || !DATE.test(to) || Number.isNaN(Date.parse(from)) || Number.isNaN(Date.parse(to)) || from > to || dayCount(from, to) > 366;
const SCOPES: [string, string][] = [
  ['time', '시간 기록만'],
  ['all', '전체'],
  ['detail', '세부내용만'],
];

// 웹앱 stats-dialog.tsx의 데이터 내보내기와 같은 규칙이다. 기본 기간은 보고 있는 날짜의 달이다.
// 확장은 파일을 내려받지만 앱은 CSV 파일을 만들어 공유 시트로 넘긴다(저장·메일·메신저 등).
function CsvDialog({ day, onClose }: { day: string; onClose: () => void }) {
  const { c } = useTheme();
  const [year, month] = day.split('-').map(Number);
  const [from, setFrom] = useState(`${day.slice(0, 8)}01`);
  const [to, setTo] = useState(`${day.slice(0, 8)}${String(new Date(Date.UTC(year, month, 0)).getUTCDate()).padStart(2, '0')}`);
  const [scope, setScope] = useState('time');
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const invalid = csvInvalid(from, to);

  const exportCsv = async () => {
    if (exporting || invalid) return;
    setExporting(true);
    setError('');
    try {
      const bytes = await download(`/api/export?from=${from}&to=${to}&scope=${scope}`);
      if (!bytes) return;
      const file = new File(Paths.cache, `work-timer_${from}_${to}.csv`);
      file.create({ overwrite: true });
      await file.write(bytes);
      if (!(await Sharing.isAvailableAsync())) throw Error('이 기기에서는 파일을 공유할 수 없습니다.');
      await Sharing.shareAsync(file.uri, { mimeType: 'text/csv', dialogTitle: 'CSV 저장·공유' });
    } catch (e) {
      setError(errorText(e, '데이터를 내보내지 못했습니다.'));
    } finally {
      setExporting(false);
    }
  };

  // 웹앱 copyPrompt(): 고른 기간·범위에 맞는 AI 요약 프롬프트를 복사하고 2초 동안 알린다.
  const copyPrompt = async () => {
    if (invalid) return;
    setError('');
    try {
      await Clipboard.setStringAsync(summaryPrompt(from, to, scope));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError('프롬프트를 복사하지 못했습니다.');
    }
  };

  return (
    <Dialog visible onClose={onClose} title="데이터 내보내기" closeButton>
      <Hint>선택한 기간의 업무 기록을 CSV 파일로 저장합니다. (최대 366일)</Hint>
      <Label>시작일</Label>
      <Field value={from} onChangeText={setFrom} placeholder="YYYY-MM-DD" keyboardType="numbers-and-punctuation" maxLength={10} />
      <Label>종료일</Label>
      <Field value={to} onChangeText={setTo} placeholder="YYYY-MM-DD" keyboardType="numbers-and-punctuation" maxLength={10} />
      <Label>내보내기 범위</Label>
      <View style={styles.scopes}>
        {SCOPES.map(([value, text]) => (
          <Pressable key={value} accessibilityRole="radio" accessibilityState={{ checked: scope === value }} onPress={() => setScope(value)} style={styles.radio} hitSlop={6}>
            <View style={[styles.dot, { borderColor: scope === value ? c.accent : c.line }]}>
              {scope === value ? <View style={[styles.dotIn, { backgroundColor: c.accent }]} /> : null}
            </View>
            <Text style={{ color: c.fg, fontSize: 14 }}>{text}</Text>
          </Pressable>
        ))}
      </View>
      <Hint>세부내용(업무 내용·결과)이 들어가는 범위를 고르면 파일 크기와 AI 작업량이 늘어납니다.</Hint>
      <Hint>CSV를 AI에 첨부할 때 같은 범위로 복사한 프롬프트를 함께 붙여넣으세요.</Hint>
      <ErrorText>{from && to && invalid ? '기간을 확인해 주세요. (최대 366일)' : ''}</ErrorText>
      <ErrorText>{error}</ErrorText>
      <Row>
        <Button title="취소" variant="outline" onPress={onClose} style={s.flex} />
        <Button title={exporting ? '내보내는 중…' : 'CSV 저장·공유'} disabled={exporting || invalid} onPress={exportCsv} style={s.flex} />
      </Row>
      <Button title={copied ? '복사했습니다' : 'AI 요약 프롬프트 복사'} variant="outline" disabled={invalid} onPress={copyPrompt} />
    </Dialog>
  );
}

const styles = StyleSheet.create({
  main: { padding: 16, gap: 4 },
  head: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  total: { fontSize: 30 },
  scopes: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, marginVertical: 4 },
  radio: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { width: 20, height: 20, borderRadius: 10, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  dotIn: { width: 10, height: 10, borderRadius: 5 },
});
