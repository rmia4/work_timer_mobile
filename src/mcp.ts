import { accessToken } from './auth';
import { RESOURCE } from './config';
// Work Timer MCP 서버(/api/mcp, Streamable HTTP, stateless)의 도구를 JSON-RPC tools/call로 직접 부른다.
// 서버가 stateless라 initialize 없이 요청마다 독립적으로 호출할 수 있다.

// 서버의 taskView 결과. 시각은 한국 시간 ISO 문자열, 시간 길이는 분 단위다.
export type Task = {
  id: string;
  day: string;
  title: string;
  status: 'running' | 'paused' | 'done';
  minutes: number;
  target_minutes: number | null;
  started_at: string | null;
  ended_at: string | null;
  version: number;
  note?: string;
  result?: string;
};

// 도구가 isError로 답한 경우. message는 서버가 보낸 사용자용 문장이다.
export class ToolError extends Error {}

let nextId = 1;
async function post(name: string, args: object, token: string) {
  return fetch(RESOURCE, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
    body: JSON.stringify({ jsonrpc: '2.0', id: nextId++, method: 'tools/call', params: { name, arguments: args } }),
  });
}

// 응답은 JSON이거나 SSE(data: 줄에 JSON-RPC 메시지)다.
async function readMessage(res: Response) {
  const text = await res.text();
  if (!(res.headers.get('content-type') ?? '').includes('text/event-stream')) return JSON.parse(text);
  const messages = text
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => JSON.parse(line.slice(5)));
  const reply = messages.find((m) => 'result' in m || 'error' in m);
  if (!reply) throw new Error('서버 응답을 읽지 못했습니다.');
  return reply;
}

export async function callTool<T>(name: string, args: object = {}): Promise<T> {
  let res = await post(name, args, await accessToken());
  // 토큰이 서버에서 폐기·만료되었으면 한 번만 갱신해서 다시 보낸다.
  if (res.status === 401) res = await post(name, args, await accessToken(true));
  if (!res.ok) throw new Error(`서버 오류(HTTP ${res.status})`);
  const message = await readMessage(res);
  if (message.error) throw new Error(message.error.message ?? '요청이 실패했습니다.');
  const result = message.result;
  if (result.isError) throw new ToolError(result.content?.[0]?.text ?? '요청이 실패했습니다.');
  return result.structuredContent as T;
}

export const getCurrentWork = () => callTool<{ task: Task | null }>('get_current_work').then((r) => r.task);
export const listTodayTasks = () => callTool<{ day: string; tasks: Task[] }>('list_tasks');
export const startWork = (title: string, targetMinutes?: number) =>
  callTool('start_work', targetMinutes ? { title, target_minutes: targetMinutes } : { title });
export const pauseWork = () => callTool('pause_work');
export const resumeWork = () => callTool('resume_work');
export const finishWork = (summary: string) => callTool('finish_work', { summary });
