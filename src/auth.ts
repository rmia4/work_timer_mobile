import * as AuthSession from 'expo-auth-session';
import * as SecureStore from 'expo-secure-store';
import { CLIENT_NAME, ORIGIN, REDIRECT_URI, RESOURCE, SCOPES } from './config';
// Work Timer 서버의 OAuth 2.1(PKCE S256, 공개 클라이언트)로 로그인한다.
// client_id는 처음 로그인할 때 동적 등록(/oauth/register)으로 받아 저장해 두고, 토큰은 SecureStore에 둔다.
// access 토큰은 1시간, refresh 토큰은 30일이고 refresh할 때마다 새로 받는다(이미 쓴 refresh 토큰을 다시 보내면 서버가 연결을 끊는다).

const CLIENT_KEY = 'oauth_client_id';
const TOKENS_KEY = 'oauth_tokens';
type Tokens = { accessToken: string; refreshToken: string; expiresAt: number };

export class OAuthError extends Error {
  constructor(public code: string, description: string) {
    super(description);
  }
}
// 저장된 토큰을 더 쓸 수 없어 다시 로그인해야 할 때 던진다.
export class SignedOutError extends Error {
  constructor() {
    super('다시 로그인해 주세요.');
  }
}

let signedOutListener: (() => void) | null = null;
export const onSignedOut = (listener: () => void) => {
  signedOutListener = listener;
};

async function postForm(path: string, params: Record<string, string>) {
  const res = await fetch(ORIGIN + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
    body: new URLSearchParams(params).toString(),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new OAuthError(body.error ?? 'server_error', body.error_description ?? `HTTP ${res.status}`);
  return body;
}

async function clientId() {
  const saved = await SecureStore.getItemAsync(CLIENT_KEY);
  if (saved) return saved;
  const res = await fetch(ORIGIN + '/oauth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      client_name: CLIENT_NAME,
      redirect_uris: [REDIRECT_URI],
      grant_types: ['authorization_code', 'refresh_token'],
      response_types: ['code'],
      token_endpoint_auth_method: 'none',
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok || typeof body.client_id !== 'string')
    throw new OAuthError(body.error ?? 'server_error', body.error_description ?? `앱 등록 실패(HTTP ${res.status})`);
  await SecureStore.setItemAsync(CLIENT_KEY, body.client_id);
  return body.client_id as string;
}

async function requestTokens(params: Record<string, string>): Promise<Tokens> {
  const body = await postForm('/oauth/token', { ...params, client_id: await clientId(), resource: RESOURCE });
  const tokens = { accessToken: body.access_token, refreshToken: body.refresh_token, expiresAt: Date.now() + body.expires_in * 1000 };
  await SecureStore.setItemAsync(TOKENS_KEY, JSON.stringify(tokens));
  return tokens;
}

async function savedTokens(): Promise<Tokens | null> {
  const raw = await SecureStore.getItemAsync(TOKENS_KEY);
  return raw ? JSON.parse(raw) : null;
}
export const isSignedIn = async () => (await savedTokens()) !== null;

// 브라우저(Custom Tabs)에서 로그인·동의를 받고 토큰을 저장한다. 사용자가 취소하면 false.
export async function signIn() {
  const id = await clientId();
  const request = new AuthSession.AuthRequest({ clientId: id, redirectUri: REDIRECT_URI, scopes: SCOPES, extraParams: { resource: RESOURCE } });
  const result = await request.promptAsync({ authorizationEndpoint: ORIGIN + '/oauth/authorize' });
  if (result.type === 'cancel' || result.type === 'dismiss') return false;
  if (result.type !== 'success') {
    const error = result.type === 'error' ? result.error : null;
    throw new OAuthError(error?.code ?? result.type, error?.description ?? '로그인하지 못했습니다.');
  }
  // RFC 9207: 응답을 보낸 인가 서버가 맞는지 확인한다(state 검사는 AuthRequest가 한다).
  if (result.params.iss !== ORIGIN) throw new OAuthError('invalid_issuer', '알 수 없는 서버의 응답입니다.');
  await requestTokens({ grant_type: 'authorization_code', code: result.params.code, redirect_uri: REDIRECT_URI, code_verifier: request.codeVerifier! });
  return true;
}

async function clearTokens() {
  await SecureStore.deleteItemAsync(TOKENS_KEY);
}

// refresh는 동시에 한 번만 한다. 같은 refresh 토큰을 두 번 보내면 서버가 연결을 끊기 때문이다.
let refreshing: Promise<Tokens> | null = null;
function refresh(tokens: Tokens) {
  refreshing ??= requestTokens({ grant_type: 'refresh_token', refresh_token: tokens.refreshToken })
    .catch(async (e) => {
      if (e instanceof OAuthError && e.code === 'invalid_grant') {
        await clearTokens();
        signedOutListener?.();
        throw new SignedOutError();
      }
      throw e;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

// 유효한 access 토큰. 만료 1분 전부터 미리 갱신한다. force면 만료 전이라도 갱신한다(서버가 401을 준 경우).
export async function accessToken(force = false) {
  if (refreshing) return (await refreshing).accessToken;
  const tokens = await savedTokens();
  if (!tokens) {
    signedOutListener?.();
    throw new SignedOutError();
  }
  if (!force && tokens.expiresAt - 60_000 > Date.now()) return tokens.accessToken;
  return (await refresh(tokens)).accessToken;
}

// refresh 토큰을 폐기하면 서버가 연결 전체를 끊는다. 네트워크 오류가 나도 로컬 토큰은 지운다.
export async function signOut() {
  const tokens = await savedTokens();
  const id = await SecureStore.getItemAsync(CLIENT_KEY);
  await clearTokens();
  if (tokens && id)
    await postForm('/oauth/revoke', { token: tokens.refreshToken, token_type_hint: 'refresh_token', client_id: id }).catch(() => {});
}
