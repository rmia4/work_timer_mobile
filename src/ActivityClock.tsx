import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, G, Line, Text as SvgText } from 'react-native-svg';
import { duration, elapsedOf, statusLabel, type Task } from './dates';
import { useTheme } from './theme';
import { s as ui } from './ui';

// 크롬 확장 activity-clock.js(웹앱 daily-activity-clock.tsx)의 하루 활동 시계.
// 마우스를 올리는 대신 구간을 누르면 작업 정보를 시계 아래 카드에 보여 주고, 같은 작업의 구간을 함께 강조한다.
const DAY_MS = 24 * 60 * 60 * 1000;
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const RADIUS = 98;
const LANE_GAP = 15;
const GUIDE_RADIUS = RADIUS - LANE_GAP * 2;
const LABEL_RADIUS = RADIUS - LANE_GAP * 3;
const NEARBY_GAP_MS = 40 * 60 * 1000;
const GOLDEN_ANGLE = 137.508;
const MARKS = Array.from({ length: 8 }, (_, i) => ({ hour: i * 3, angle: (i / 8) * Math.PI * 2 - Math.PI / 2 }));

export type Segment = { task: Task; color: string; start: number; end: number; startRatio: number; durationRatio: number; lane: number };

const firstStart = (t: Task) => t.sessions?.[0]?.start_at ?? t.started_at ?? 0;

function idHue(id: string) {
  let hash = 2166136261;
  for (const char of id) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619);
  return (hash >>> 0) % 360;
}

function taskColors(tasks: Task[]) {
  const minGap = Math.min(30, 360 / Math.max(1, tasks.length) / 1.5);
  const used: number[] = [];
  const colors = new Map<string, string>();
  for (const task of [...tasks].sort((a, b) => firstStart(a) - firstStart(b) || a.id.localeCompare(b.id))) {
    let hue = idHue(task.id);
    for (let attempt = 0; attempt < 24; attempt += 1) {
      if (used.every((o) => Math.min(Math.abs(hue - o), 360 - Math.abs(hue - o)) >= minGap)) break;
      hue = (hue + GOLDEN_ANGLE) % 360;
    }
    used.push(hue);
    colors.set(task.id, `hsl(${Math.round(hue)}, 62%, 76%)`);
  }
  return colors;
}

// 하루(한국 시간) 안으로 자른 측정 구간. 가까운 구간은 안쪽 줄로 옮겨 겹치지 않게 한다.
export function activitySegments(tasks: Task[], day: string, now: number) {
  const dayStart = Date.parse(`${day}T00:00:00+09:00`);
  const dayEnd = dayStart + DAY_MS;
  const colors = taskColors(tasks);
  const segments: Segment[] = tasks.flatMap((task) => {
    const spans = task.sessions?.length
      ? task.sessions.map((s) => ({ start: s.start_at, end: s.end_at }))
      : task.started_at === null
        ? []
        : [{ start: task.started_at, end: task.ended_at ?? (task.status === 'done' ? task.started_at : null) }];
    return spans.flatMap((span) => {
      const start = Math.max(dayStart, span.start);
      const end = Math.min(dayEnd, span.end ?? now);
      if (end <= start) return [];
      return [{ task, color: colors.get(task.id)!, start, end, startRatio: (start - dayStart) / DAY_MS, durationRatio: (end - start) / DAY_MS, lane: 0 }];
    });
  });
  let previous: Segment | null = null;
  for (const s of segments.sort((a, b) => a.start - b.start || a.end - b.end)) {
    s.lane = previous && s.start - previous.end <= NEARBY_GAP_MS && previous.lane === 0 ? 1 : 0;
    previous = s;
  }
  return segments;
}

const laneRadius = (s: Segment) => Math.max(44, RADIUS - s.lane * LANE_GAP);

// 누른 점(시계 좌표 0~240)에 있는 구간. SVG 원의 터치 판정은 점선으로 숨긴 부분과 안쪽까지 잡아
// 바깥 줄 원이 안쪽 줄을 가리므로, 중심에서의 거리로 줄을 고르고 각도로 구간을 고른다.
function segmentAt(segments: Segment[], x: number, y: number) {
  const dx = x - 120;
  const dy = y - 120;
  const dist = Math.hypot(dx, dy);
  const ratio = ((Math.atan2(dy, dx) + Math.PI / 2) / (Math.PI * 2) + 1) % 1;
  let best: Segment | null = null;
  let bestGap = Infinity;
  for (const s of segments) {
    const r = laneRadius(s);
    if (Math.abs(dist - r) > LANE_GAP / 2) continue;
    const slop = 10 / (Math.PI * 2 * r); // 둥근 끝과 손가락 크기만큼 조금 넓게 잡는다
    const gap = Math.max(0, s.startRatio - ratio, ratio - s.startRatio - s.durationRatio);
    if (gap <= slop && gap < bestGap) {
      best = s;
      bestGap = gap;
    }
  }
  return best;
}

const hm = (t: number) => new Date(t + KST_OFFSET_MS).toISOString().slice(11, 16);

// 고른 구간은 부모가 들고 있다(화면의 빈 곳을 누르거나 다른 탭으로 가면 부모가 지운다).
export default function ActivityClock({
  segments,
  now,
  picked,
  setPicked,
}: {
  segments: Segment[];
  now: number;
  picked: Segment | null;
  setPicked: (s: Segment | null) => void;
}) {
  const { c } = useTheme();
  const [size, setSize] = useState(0);
  const angle = (((now + KST_OFFSET_MS) % DAY_MS) / DAY_MS) * Math.PI * 2 - Math.PI / 2;
  const tip = picked && segments.find((s) => s.task.id === picked.task.id && s.start === picked.start);
  return (
    <View>
      <Pressable
        style={styles.clock}
        onLayout={(e) => setSize(e.nativeEvent.layout.width)}
        onPress={(e) => size && setPicked(segmentAt(segments, (e.nativeEvent.locationX * 240) / size, (e.nativeEvent.locationY * 240) / size))}
      >
        <Svg viewBox="0 0 240 240" width="100%" height="100%" accessibilityLabel="하루 활동 시계">
          <Circle cx={120} cy={120} r={RADIUS} fill={c.bg} stroke={c.line} strokeWidth={12} />
          {segments.map((s, i) => {
            const r = laneRadius(s);
            const len = 2 * Math.PI * r;
            return (
              <Circle
                key={i}
                cx={120}
                cy={120}
                r={r}
                fill="none"
                stroke={s.color}
                strokeWidth={tip?.task.id === s.task.id ? 16 : 12}
                strokeLinecap="round"
                strokeDasharray={`${s.durationRatio * len} ${len}`}
                strokeDashoffset={-s.startRatio * len}
                rotation={-90}
                originX={120}
                originY={120}
              />
            );
          })}
          <G>
            <Circle cx={120} cy={120} r={GUIDE_RADIUS} fill="none" stroke={c.line} />
            {MARKS.map(({ angle: a }) => (
              <Circle key={a} cx={120 + Math.cos(a) * GUIDE_RADIUS} cy={120 + Math.sin(a) * GUIDE_RADIUS} r={3} fill={c.muted} />
            ))}
          </G>
          <Line x1={120} y1={120} x2={120 + Math.cos(angle) * RADIUS} y2={120 + Math.sin(angle) * RADIUS} stroke={c.muted} strokeWidth={2} strokeLinecap="round" />
          <Circle cx={120} cy={120} r={5} fill={c.muted} stroke={c.bg} strokeWidth={2} />
          {MARKS.map(({ hour, angle: a }) => (
            <SvgText
              key={hour}
              x={120 + Math.cos(a) * LABEL_RADIUS}
              y={120 + Math.sin(a) * LABEL_RADIUS + 3}
              fill={c.muted}
              fontSize={9}
              fontWeight="600"
              textAnchor="middle"
            >
              {String(hour)}
            </SvgText>
          ))}
        </Svg>
      </Pressable>
      {/* 고르지 않았을 때도 자리를 비워 두어 구간을 고르거나 닫아도 화면이 움직이지 않는다. */}
      <View style={[styles.card, tip && { backgroundColor: c.bg, borderColor: c.line }]}>
        {tip ? <TaskInfo tip={tip} segments={segments.filter((x) => x.task.id === tip.task.id)} now={now} /> : null}
      </View>
    </View>
  );
}

// 고른 구간의 작업 정보. 제목은 한 줄로 자른다.
function TaskInfo({ tip, segments, now }: { tip: Segment; segments: Segment[]; now: number }) {
  const { c } = useTheme();
  const t = tip.task;
  const rows: [string, string][] = [
    ['이 구간', `${hm(tip.start)}–${hm(tip.end)} (${duration(tip.end - tip.start)})`],
    ['이 날 측정', `${duration(segments.reduce((sum, x) => sum + x.end - x.start, 0))} · 구간 ${segments.length}개`],
    ['총 소요', duration(elapsedOf(t, now))],
  ];
  return (
    <>
      <View style={styles.title}>
        <View style={[styles.dot, { backgroundColor: tip.color }]} />
        <Text numberOfLines={1} style={[styles.titleText, { color: c.fg }]}>
          {t.title}
        </Text>
        <Text style={{ color: c.muted, fontSize: 12 }}>{statusLabel(t.status)}</Text>
      </View>
      {rows.map(([label, value]) => (
        <View key={label} style={styles.row}>
          <Text style={[styles.label, { color: c.muted }]}>{label}</Text>
          <Text style={[ui.tabular, { color: c.fg, fontSize: 13 }]}>{value}</Text>
        </View>
      ))}
    </>
  );
}

const styles = StyleSheet.create({
  clock: { width: '100%', maxWidth: 360, aspectRatio: 1, alignSelf: 'center', marginTop: 8 },
  card: { height: 108, marginTop: 12, paddingVertical: 10, paddingHorizontal: 12, gap: 4, borderWidth: 1, borderColor: 'transparent', borderRadius: 8, justifyContent: 'center' },
  title: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 2 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  titleText: { flex: 1, fontSize: 15, fontWeight: '600' },
  row: { flexDirection: 'row', gap: 12 },
  label: { width: 64, fontSize: 13 },
});
