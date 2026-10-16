import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { errorText, get } from './api';
import { duration, today } from './dates';
import { useTheme } from './theme';
import { Button, ErrorText, Hint } from './ui';

// 크롬 확장 calendar.js(웹앱 work-calendar.tsx)의 작업 캘린더. 달마다 날짜별 작업 수·시간을 불러와 캐시한다.
type Month = { days: Record<string, { count: number; total: number; running?: number }>; now: number };
const monthCache = new Map<string, Month>(); // "YYYY-MM" → 그 달 기록

export function invalidateCalendarCache(day?: string) {
  if (day) monthCache.delete(day.slice(0, 7));
  else monthCache.clear();
}

const summaryDuration = (ms: number) => duration(ms).split(':').slice(0, 2).join(':');
const shiftMonth = (key: string, n: number) => {
  const d = new Date(`${key}-01T00:00:00Z`);
  d.setUTCMonth(d.getUTCMonth() + n);
  return d.toISOString().slice(0, 7);
};

// 웹앱처럼 날짜를 한 번 누르면 고르고, 고른 날짜를 다시 누르면 onChoose를 부른다.
export default function Calendar({ day, version, onChoose }: { day: string; version: number; onChoose: (day: string) => void }) {
  const { c } = useTheme();
  const [selection, setSelection] = useState(day);
  const [month, setMonth] = useState(day.slice(0, 7));
  const [, setLoaded] = useState(0);
  const [error, setError] = useState('');
  const cached = monthCache.get(month);

  useEffect(() => {
    if (monthCache.has(month) || error) return;
    let stale = false;
    get(`/api/calendar?month=${month}`)
      .then((data) => {
        if (!data) return;
        monthCache.set(month, { days: Object.fromEntries(data.days.map((d: { day: string }) => [d.day, d])), now: data.now });
        if (!stale) setLoaded((n) => n + 1);
      })
      .catch((e) => !stale && setError(errorText(e, '캘린더를 불러오지 못했습니다.')));
    return () => {
      stale = true;
    };
  }, [month, error, version]);

  const select = (value: string) => (value === selection ? onChoose(value) : setSelection(value));
  const move = (n: number) => {
    setMonth(shiftMonth(month, n));
    setError('');
  };
  // 측정 중인 작업 시간은 불러온 시각 이후만큼 더한다.
  const total = (d?: Month['days'][string]) => (d?.total || 0) + (d?.running || 0) * Math.max(0, Date.now() - (cached?.now ?? Date.now()));
  const todayKey = today();
  const first = new Date(`${month}-01T00:00:00Z`);
  const count = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate();
  const cells: (string | null)[] = [...Array(first.getUTCDay()).fill(null), ...Array.from({ length: count }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`)];
  const s = cached?.days[selection];

  return (
    <View>
      <View style={styles.head}>
        <Text style={[styles.title, { color: c.fg }]}>작업 캘린더</Text>
        <Button
          title="오늘"
          variant="outline"
          small
          onPress={() => {
            setMonth(todayKey.slice(0, 7));
            setError('');
            select(todayKey);
          }}
        />
      </View>
      <View style={styles.head}>
        <Button title="‹" label="이전 달" variant="outline" small onPress={() => move(-1)} />
        <Text style={{ color: c.fg, fontWeight: '600', fontSize: 15 }}>
          {Number(month.slice(0, 4))}년 {Number(month.slice(5))}월
        </Text>
        <Button title="›" label="다음 달" variant="outline" small onPress={() => move(1)} />
      </View>
      <View style={styles.grid}>
        {'일월화수목금토'.split('').map((w) => (
          <Text key={w} style={[styles.cell, styles.weekday, { color: c.muted }]}>
            {w}
          </Text>
        ))}
        {cells.map((key, i) => {
          if (!key) return <View key={`e${i}`} style={styles.cell} />;
          const d = cached?.days[key];
          const wd = i % 7;
          const selected = key === selection;
          return (
            <Pressable
              key={key}
              accessibilityRole="button"
              accessibilityLabel={`${key}, ${cached ? `작업 ${d?.count || 0}개 · 총 ${duration(total(d))}` : '기록 확인 중'}`}
              onPress={() => select(key)}
              style={[styles.cell, styles.day, { borderColor: selected ? c.accent : key === todayKey ? c.line : 'transparent' }]}
            >
              <Text style={{ fontSize: 14, fontWeight: selected ? '600' : '400', color: selected ? c.accent : wd === 0 ? c.danger : wd === 6 ? c.accent : c.fg }}>
                {Number(key.slice(8))}
              </Text>
              {cached && d && d.count > 0 ? <Text style={{ fontSize: 10, color: c.muted }}>{d.count}개</Text> : null}
            </Pressable>
          );
        })}
      </View>
      {error ? (
        <ErrorText>{error}</ErrorText>
      ) : (
        <Hint style={{ marginTop: 6 }}>
          {cached && selection.slice(0, 7) === month ? `작업 ${s?.count || 0}개, ${summaryDuration(total(s))}` : cached ? '날짜를 선택해 주세요.' : '기록을 불러오는 중입니다.'}
        </Hint>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  head: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginBottom: 6 },
  title: { fontSize: 16, fontWeight: '600' },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  cell: { width: `${100 / 7}%`, alignItems: 'center' },
  weekday: { fontSize: 12, textAlign: 'center', paddingVertical: 2 },
  day: { minHeight: 44, justifyContent: 'center', borderWidth: 1, borderRadius: 6 },
});
