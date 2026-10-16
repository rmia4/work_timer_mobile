# Work Timer 모바일

[Work Timer](https://github.com/rmia4/work_timer) 웹앱의 모바일 앱(Expo, React Native)입니다. 지금은 Android만 대상으로 합니다.

## 기능(1단계)

- Work Timer 계정으로 로그인(OAuth)
- 진행 중인 작업 표시, 시작·일시정지·재개·종료
- 오늘 기록 목록

## 서버 연동

서버 API를 따로 두지 않고 웹앱의 MCP용 OAuth와 MCP 엔드포인트를 그대로 씁니다.

- 로그인: OAuth 2.1 인가 코드 + PKCE(S256), 공개 클라이언트. 처음 로그인할 때 `/oauth/register`로 클라이언트를 등록하고 `client_id`를 기기에 저장합니다.
- redirect URI: `io.github.rmia4.worktimer:/oauth` (앱 패키지 ID와 같은 역도메인 스킴)
- 토큰: access 1시간, refresh 30일. `expo-secure-store`에 저장하고 refresh할 때마다 새 refresh 토큰으로 바꿉니다.
- 데이터: `<ORIGIN>/api/mcp`에 JSON-RPC `tools/call`을 보냅니다(`get_current_work`, `list_tasks`, `start_work`, `pause_work`, `resume_work`, `finish_work`).
- 로그아웃하면 refresh 토큰을 폐기해 서버의 연결도 끊습니다. 웹의 설정 > 연결된 앱에서 해제해도 됩니다.

서버 주소는 [src/config.ts](src/config.ts)에 있습니다.

## 개발

Node 24, pnpm, Android Studio(SDK, 에뮬레이터)가 필요합니다. 커스텀 스킴 redirect를 써서 Expo Go로는 로그인할 수 없고, 개발 빌드로 실행합니다.

```bash
pnpm install
npx expo run:android
```

- `ANDROID_HOME`: Android SDK 경로(예: `%LOCALAPPDATA%\Android\Sdk`)
- `JAVA_HOME`: JDK 17. 경로에 괄호가 있으면 Gradle·SDK 스크립트가 실패하므로 8.3 짧은 경로(`C:\PROGRA~2\...`)나 괄호 없는 경로를 씁니다.
- Windows 긴 경로: C++ 코드 생성 결과 경로가 260자를 넘으므로 `LongPathsEnabled`를 켜야 합니다(관리자 PowerShell, 재부팅 필요).
  `New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name LongPathsEnabled -Value 1 -PropertyType DWORD -Force`
- CMake 3.31.6: 긴 경로를 다루는 ninja가 들어 있습니다. [plugins/with-cmake-version.js](plugins/with-cmake-version.js)가 앱 빌드에 이 버전을 지정합니다. 없으면 Android Studio SDK Manager나 `sdkmanager "cmake;3.31.6"`으로 설치합니다.
- pnpm은 `pnpm-workspace.yaml`의 `nodeLinker: hoisted`로 평평한 `node_modules`를 씁니다(네이티브 빌드 경로를 줄이기 위함).
- 타입 검사: `npx tsc --noEmit`

`android/`, `ios/`는 `expo prebuild`가 만드는 폴더라 커밋하지 않습니다. 네이티브 설정은 `app.json`에서 합니다.

## 브랜치

`main`은 배포용이고 `dev`에서만 병합합니다. 작업은 `dev`에서 브랜치를 따서 `dev`로 PR을 엽니다.
