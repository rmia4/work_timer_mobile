# TODO

## 서버 푸시로 기록 중 작업 알림을 바로 갱신 (모바일·웹·확장)

지금은 다른 곳(웹·확장·AI)에서 바꾼 기록이 앱을 열 때만 알림에 반영된다. 서버가 기록이 바뀌는 순간 각 기기에 푸시를 보내게 한다.

- 서버에서 기록을 바꾸는 길은 웹 API(`app/api/tasks/route.ts`)와 MCP(`lib/mcp.ts`) 모두 `lib/task-service.ts`의 `applyTaskAction` 하나다. 푸시도 여기 뒤에 붙인다.
- Android는 FCM, 웹·확장은 표준 Web Push(VAPID)를 쓴다. 확장은 번들러가 없어서 Firebase SDK를 넣기 번거롭기 때문이다.
- 푸시에는 "바뀌었음"만 담는다. 받은 기기가 `/api/tasks`를 다시 조회해 알림을 그린다.

### 먼저 준비 (직접)
- [ ] Firebase 프로젝트 만들고 Android 앱(`io.github.rmia4.worktimer`) 등록 → `app/google-services.json`
- [ ] FCM 서비스 계정 키, VAPID 키 쌍 → Vercel 환경 변수

### 1. 서버 (work_timer)
- [ ] 기기 테이블(owner, kind: android|web|extension, 토큰 또는 구독 JSON, updated)과 마이그레이션(drizzle, drizzle-neon)
- [ ] `/api/devices` 등록·삭제, VAPID 공개 키 경로
- [ ] `applyTaskAction` 성공 뒤 `notify(owner)`를 부른다. `after()`로 응답을 늦추지 않고, 실패해도 저장에는 영향이 없게 한다. 404·410·UNREGISTERED가 오면 그 기기를 지운다.
- [ ] 테스트

### 2. 모바일 (이 저장소)
- [ ] `libs.versions.toml`에 firebase-messaging과 google-services 추가
- [ ] `FirebaseMessagingService`를 만든다.
  - 토큰은 로그인할 때와 onNewToken에서 등록하고, 로그아웃할 때 지운다.
  - 메시지를 받으면 진행 중 작업을 조회해 `TaskNotification.update`를 부른다. `TaskActionReceiver`의 새로고침 코드는 공용 함수로 빼서 같이 쓴다.

### 3. 웹 (work_timer)
- [ ] `public/sw.js`를 만든다.
  - push를 받으면 `showNotification`을 띄운다(tag `task`, 버튼 2개: 일시정지/다시 시작, 종료·저장).
  - 종료·저장을 누르면 `/?finish=<id>`를 열어 결과 저장 대화상자를 띄운다.
- [ ] 설정에 알림 권한과 3단 선택(알림 없음 / 이 기기에서 실행만 / 모두)을 넣는다. 이 기기에서 실행한 작업 id는 IndexedDB에 둔다.

### 4. 확장 (work_timer_extension)
- [ ] manifest에 `background` 서비스 워커와 `notifications` 권한을 추가하고 버전을 올린다.
- [ ] `background.js`를 만든다. 구독은 `pushManager.subscribe(userVisibleOnly:false)`로 하고, 알림은 `chrome.notifications`로 버튼 2개와 `requireInteraction`을 쓴다.
- [ ] 팝업 설정에 3단 선택, `STORE.md`에 권한 변경 사유 → 웹스토어 재심사

### 한계
- 웹·확장 알림에는 흐르는 시간(크로노미터)이 없다. 그래서 시작 시각을 보여 준다.
- 크롬이 백그라운드에서라도 실행 중이어야 받는다.
- 웹은 푸시마다 알림을 반드시 보여 줘야 한다. 그래서 작업이 끝나면 알림을 지우지 않고 "작업 종료" 알림으로 바꾼다.
- 앱을 강제 종료하면 FCM을 받지 못한다. 구글 Play 서비스가 없는 기기에서도 안 된다.
