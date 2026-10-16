// 서버(Work Timer 웹앱)와 OAuth 클라이언트 설정.
export const ORIGIN = 'https://work-timer-mauve-seven.vercel.app';
export const RESOURCE = ORIGIN + '/api/mcp';
// 서버는 역도메인 형식의 비공개 스킴만 redirect URI로 받는다. app.json의 scheme·android.package와 맞춘다.
export const REDIRECT_URI = 'io.github.rmia4.worktimer:/oauth';
export const CLIENT_NAME = 'Work Timer 모바일';
export const SCOPES = ['tasks:read', 'tasks:write'];
