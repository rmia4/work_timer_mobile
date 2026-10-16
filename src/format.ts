// 서버 값은 분 단위 길이와 한국 시간 ISO 문자열(2026-10-15T10:00:00+09:00)이다.
export function duration(minutes: number) {
  const h = Math.floor(minutes / 60), m = minutes % 60;
  return h ? `${h}시간 ${m}분` : `${m}분`;
}
export const clock = (iso: string | null) => (iso ? iso.slice(11, 16) : '');
export const STATUS_LABEL = { running: '진행 중', paused: '일시정지', done: '완료' } as const;
