import { ORIGIN } from './config';
// 크롬 확장 api.js와 같은 웹앱 API를 로그인 쿠키(네이티브 쿠키 저장소)로 부른다.
// 앱은 Origin을 보내지 않으므로 서버가 쓰기 요청을 허용하도록 모바일 헤더를 붙인다(웹앱 lib/auth.ts trustedOrigin).

const HEADERS = { 'x-work-timer-client': 'mobile', Accept: 'application/json' };

let unauthorized = () => {};
// 세션이 끝났을 때(401) 할 일을 정한다.
export const onUnauthorized = (fn: () => void) => {
  unauthorized = fn;
};

export type Result = { ok: boolean; status: number; data: any };

export async function call(path: string, { method = 'GET', body }: { method?: string; body?: unknown } = {}): Promise<Result> {
  const json = body === undefined ? undefined : JSON.stringify(body);
  const r = await fetch(ORIGIN + path, {
    method,
    credentials: 'include',
    headers: json === undefined ? HEADERS : { ...HEADERS, 'Content-Type': 'application/json' },
    body: json,
  });
  const data = await r.json().catch(() => ({}));
  return { ok: r.ok, status: r.status, data };
}

// 로그인이 필요한 API. 401이면 로그인 화면으로 보내고 null을 돌려준다.
export async function authed(path: string, options?: { method?: string; body?: unknown }) {
  const res = await call(path, options);
  if (res.status !== 401) return res;
  unauthorized();
  return null;
}

// 로그인이 필요한 GET. 실패하면 서버 오류 문구로 던지고, 401이면 null.
export async function get(path: string) {
  const res = await authed(path);
  if (!res) return null;
  if (!res.ok) throw Error(res.data.error);
  return res.data;
}

// 로그인이 필요한 파일 받기. 바이트를 그대로 돌려준다(CSV 앞의 BOM을 지키기 위해 text()로 읽지 않는다).
export async function download(path: string) {
  const r = await fetch(ORIGIN + path, { credentials: 'include', headers: HEADERS });
  if (r.status === 401) {
    unauthorized();
    return null;
  }
  if (!r.ok) {
    const data = await r.json().catch(() => ({}));
    throw Error(data.error || '데이터를 내보내지 못했습니다.');
  }
  return new Uint8Array(await r.arrayBuffer());
}

// 슬라이딩 만료: 앱을 열 때마다 불러 세션을 7일 뒤로 늘린다(남은 기간이 절반 이하일 때만 서버가 늘린다).
export const renewSession = () => call('/api/auth', { method: 'PATCH' }).catch(() => null);

export const errorText = (e: unknown, fallback = '연결을 확인해 주세요.') => (e instanceof Error && e.message ? e.message : fallback);
