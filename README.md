# Work Timer Android

[Work Timer](https://github.com/rmia4/work_timer) 웹앱의 Android 앱입니다. Kotlin과 Jetpack Compose로 만들었습니다.

## 실행

1. Android Studio에서 이 폴더를 엽니다(File → Open).
2. Gradle Sync가 끝나면 기기나 에뮬레이터를 고르고 ▶ Run을 누릅니다.

별도 서버(Metro 등)나 환경 변수, CMake, NDK가 필요 없습니다. Gradle JDK는 Android Studio 내장 JDK(JBR)를 그대로 씁니다.

명령줄에서 빌드할 때:

```bash
./gradlew assembleDebug        # APK 빌드
./gradlew installDebug         # 연결된 기기에 설치
./gradlew testDebugUnitTest    # 단위 테스트
./gradlew lintDebug            # lint
```

## 기능

크롬 확장 팝업([work_timer `extensions/main`](https://github.com/rmia4/work_timer/tree/extensions/main/extension))의 화면과 기능을 옮겼습니다.

- 로그인·회원가입(아이디·비밀번호)
- 상단: 현재 탭 제목과 설정 버튼. 하단: 타이머 · 일지 · 메모 · 요약 탭(각 25%)
- 타이머: 진행 중 작업의 일시정지·다시 시작·종료(업무 결과 저장), 새 기록 추가(작업명, 업무 내용/결과, 목표 시간, 소요 시간 직접 입력, 측정 시작·기록 저장)
- 일지: 날짜 이동·작업 캘린더, 기록 카드(완료 기록 수정·삭제), 일일 메모
- 메모: 메모장(추가, 제목·내용 자동 저장, 위·아래 이동, 접기, 삭제, 다른 기기와 충돌 시 선택)
- 요약: 고른 날짜의 측정 시간과 하루 활동 시계(구간을 누르면 작업과 시간 표시), CSV 내보내기(공유 시트로 저장·전송)와 AI 요약 프롬프트 복사
- 설정: 다크·라이트 모드 전환(처음엔 기기 설정), 웹페이지 방문, AI 연결(Claude·Codex 안내, 연결된 앱 해제), 계정 설정(비밀번호 변경·데이터 삭제·계정 삭제), 로그아웃
- 뒤로 가기: 대화상자·메뉴가 열려 있으면 닫고, 그 밖에는 한 번 누르면 안내를 띄우고 2초 안에 다시 누르면 앱을 닫습니다.

## 구조

```
app/src/main/java/io/github/rmia4/worktimer/
  MainActivity.kt      로그인 확인 후 로그인/메인 화면 전환, 뒤로 가기 두 번 종료
  WorkTimerApp.kt      앱 전체에서 하나만 쓰는 객체(OkHttp, Api, 설정 저장소)
  data/                Api(서버 호출), SavedCookieJar(로그인 쿠키 저장), Models
  util/                Dates(한국 시간 날짜·시간), ExportPrompt(AI 요약 프롬프트)
  ui/theme/            색(크롬 확장 popup.css), 다크·라이트 전환
  ui/components/       공통 버튼·입력·대화상자
  ui/main/             MainScreen(상단·하단 막대, 설정 메뉴), MainViewModel(기록·날짜·10초 갱신)
  ui/timer/ log/ memo/ summary/ settings/   탭과 대화상자별 화면
```

화면이 로그인과 메인 둘뿐이라 화면 이동 라이브러리 없이 상태로 전환합니다. 의존성 주입 도구도 쓰지 않고 `WorkTimerApp`에 둡니다.

## 서버 연동

확장과 같은 웹앱 API(`/api/tasks`, `/api/memos`, `/api/daily-memos`, `/api/calendar`, `/api/export`, `/api/account`, `/api/connections`, `/api/auth`)를 로그인 쿠키로 부릅니다. 서버 주소는 [data/Api.kt](app/src/main/java/io/github/rmia4/worktimer/data/Api.kt)의 `ORIGIN`에 있습니다.

- 쿠키는 OkHttp `CookieJar`가 SharedPreferences(앱 전용 저장소)에 저장해 앱을 다시 열어도 로그인이 유지됩니다.
- 앱은 Origin을 보내지 않으므로 모든 요청에 `X-Work-Timer-Client: mobile` 헤더를 붙입니다. 서버는 이 헤더가 있고 Origin이 없는 쓰기 요청을 허용합니다(웹앱 `lib/auth.ts`의 `trustedOrigin`).
- 세션은 7일입니다. 앱이 열리거나 앞으로 돌아올 때 `PATCH /api/auth`로 세션을 연장합니다.
- 10초마다 기록을 다시 불러와 다른 기기의 변경을 반영합니다.

## 브랜치

`main`은 배포용이고 `dev`에서만 병합합니다. 작업은 `dev`에서 브랜치를 따서 `dev`로 PR을 엽니다.
