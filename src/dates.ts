// 크롬 확장 dates.js(웹앱 use-workspace.ts·lib/work-dates.ts)의 날짜·시간 함수를 옮겼다.

export type Task = {
  id: string;
  day: string;
  title: string;
  note: string;
  result: string | null;
  status: 'running' | 'paused' | 'done';
  // 밀리초. started는 측정 중일 때 이번 구간의 시작 시각이다.
  elapsed: number;
  started: number | null;
  target: number;
  started_at: number | null;
  ended_at: number | null;
  version: number;
  sessions?: { start_at: number; end_at: number | null }[];
};

const pad = (n: number) => String(n).padStart(2, '0');

// 한국 시간 기준 오늘 날짜(YYYY-MM-DD).
export const today = (offset = 0) => new Date(Date.now() + 9 * 3600000 + offset * 86400000).toISOString().slice(0, 10);

export const duration = (ms: number) => {
  const s = Math.max(0, Math.floor(ms / 1000));
  return [Math.floor(s / 3600), Math.floor(s / 60) % 60, s % 60].map(pad).join(':');
};

export const statusLabel = (status: Task['status']) => (status === 'done' ? '완료' : status === 'running' ? '측정 중' : '일시정지');

// Intl 시간대 지원에 기대지 않도록 한국 시간(UTC+9)을 직접 더해 읽는다.
const kst = (t: number) => new Date(t + 9 * 3600000);

export const timeLabel = (t: number | null) => (t == null ? '미기록' : `${pad(kst(t).getUTCHours())}:${pad(kst(t).getUTCMinutes())}`);

export const timestampLabel = (t: number | null) => {
  if (t == null) return '미기록';
  const d = kst(t);
  return `${pad(d.getUTCMonth() + 1)}. ${pad(d.getUTCDate())}. ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`;
};

export const shiftDay = (value: string, offset: number) => {
  const date = new Date(`${value}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
};

export const weekday = (value: string) => '일월화수목금토'[new Date(`${value}T00:00:00Z`).getUTCDay()];

// 수정 폼에 담아 두는 시작·종료 시각(한국 시간 문자열)과 그 역변환.
export const timeInput = (t: number | null) => (t == null ? '' : new Date(t + 9 * 3600000).toISOString().slice(0, 19));
export const parseTimeInput = (t: string) => (t ? Date.parse(t + '+09:00') : null);

export const elapsedOf = (t: Task, now: number) => t.elapsed + (t.started === null ? 0 : Math.max(0, now - t.started));
