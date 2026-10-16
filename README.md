# Work Timer 모바일

[Work Timer](https://github.com/rmia4/work_timer) 웹앱의 모바일 앱(Expo, React Native)입니다. 지금은 Android만 대상으로 합니다.

## 기능

크롬 확장 팝업([work_timer `extensions/main`](https://github.com/rmia4/work_timer/tree/extensions/main/extension))의 화면과 기능을 그대로 옮겼습니다.

- 로그인·회원가입(아이디·비밀번호)
- 상단 막대: 타이머 · 일지 · 메모 · 요약, 우측 톱니 버튼의 설정 메뉴
- 타이머: 진행 중 작업의 일시정지·다시 시작·종료(업무 결과 저장), 새 기록 추가(작업명, 업무 내용/결과, 목표 시간, 소요 시간 직접 입력, 측정 시작·기록 저장)
- 일지: 날짜 이동·작업 캘린더, 기록 카드(완료 기록 수정·삭제), 일일 메모
- 메모: 메모장(추가, 제목·내용 자동 저장, 위·아래 이동, 접기, 삭제, 다른 기기와 충돌 시 선택)
- 요약: 고른 날짜의 측정 시간과 하루 활동 시계(구간을 누르면 작업과 시간 표시), CSV 내보내기(공유 시트로 저장·전송)와 AI 요약 프롬프트 복사
- 설정: 다크·라이트 모드 전환(처음엔 기기 설정), 웹페이지 방문, AI 연결(Claude·Codex 안내, 연결된 앱 해제), 계정 설정(비밀번호 변경·데이터 삭제·계정 삭제), 로그아웃

## 서버 연동

확장과 같은 웹앱 API(`/api/tasks`, `/api/memos`, `/api/daily-memos`, `/api/calendar`, `/api/export`, `/api/account`, `/api/connections`, `/api/auth`)를 로그인 쿠키로 부릅니다.

- 쿠키는 React Native(Android OkHttp)의 네이티브 쿠키 저장소가 보관하고 요청마다 붙입니다.
- 앱은 Origin을 보내지 않으므로 모든 요청에 `X-Work-Timer-Client: mobile` 헤더를 붙입니다. 서버는 이 헤더가 있고 Origin이 없는 쓰기 요청을 허용합니다(웹앱 `lib/auth.ts`의 `trustedOrigin`).
- 세션은 7일입니다. 앱이 열리거나 앞으로 돌아올 때 `PATCH /api/auth`로 세션을 연장하므로 7일 안에 한 번이라도 쓰면 다시 로그인하지 않습니다.
- 10초마다 기록을 다시 불러와 다른 기기의 변경을 반영합니다.

서버 주소는 [src/config.ts](src/config.ts)에 있습니다.

## 개발

Node 24, pnpm, Android Studio(SDK, 에뮬레이터)가 필요합니다. `react-native-svg` 등 네이티브 모듈을 쓰므로 Expo Go 대신 개발 빌드로 실행합니다.

```bash
pnpm install
npx expo run:android
```

- `ANDROID_HOME`: Android SDK 경로(예: `%LOCALAPPDATA%\Android\Sdk`)
- `JAVA_HOME`: JDK 17. 경로에 괄호가 있으면 Gradle·SDK 스크립트가 실패하므로 8.3 짧은 경로(`C:\PROGRA~2\...`)나 괄호 없는 경로를 씁니다.
- Windows 긴 경로: C++ 코드 생성 결과 경로가 260자를 넘으므로 `LongPathsEnabled`를 켜야 합니다(관리자 PowerShell, 재부팅 필요).
  `New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name LongPathsEnabled -Value 1 -PropertyType DWORD -Force`
- CMake 3.31.6: 긴 경로를 다루는 ninja가 들어 있습니다. [plugins/with-cmake-version.js](plugins/with-cmake-version.js)가 앱 빌드에 이 버전을 지정합니다. 없으면 Android Studio SDK Manager나 `sdkmanager "cmake;3.31.6"`으로 설치합니다.
- Expo 모듈은 미리 컴파일된 AAR 대신 소스에서 빌드합니다(`package.json`의 `expo.autolinking.android.buildFromSource`). SDK 58에서 `expo-crypto` 58.0.6 AAR이 `expo-modules-core` 58.0.20과 맞지 않아 `NoSuchMethodError`(…`__trampoline$ExpoModulesV2`)가 났습니다(지금은 `expo-crypto`를 쓰지 않지만 다른 모듈도 같은 문제가 날 수 있어 유지합니다).
- pnpm은 `pnpm-workspace.yaml`의 `nodeLinker: hoisted`로 평평한 `node_modules`를 씁니다(네이티브 빌드 경로를 줄이기 위함).
- 타입 검사: `npx tsc --noEmit`

`android/`, `ios/`는 `expo prebuild`가 만드는 폴더라 커밋하지 않습니다. 네이티브 설정은 `app.json`에서 합니다.

## 브랜치

`main`은 배포용이고 `dev`에서만 병합합니다. 작업은 `dev`에서 브랜치를 따서 `dev`로 PR을 엽니다.
