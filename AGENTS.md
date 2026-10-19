Kotlin + Jetpack Compose Android 앱입니다. Android만 대상으로 합니다.

## 명령

```bash
./gradlew assembleDebug        # 빌드
./gradlew installDebug         # 연결된 기기·에뮬레이터에 설치
./gradlew testDebugUnitTest    # 단위 테스트(MockWebServer로 Api·쿠키 확인)
./gradlew lintDebug            # lint
```

Windows에서 명령줄로 빌드할 때 `JAVA_HOME`은 Android Studio 내장 JDK(`C:\Program Files\Android\Android Studio\jbr`)를 씁니다. 작업을 마치기 전에 빌드·테스트·lint를 돌립니다.

## 규칙

- 의존성 버전은 `gradle/libs.versions.toml`에서만 바꿉니다. AGP·Kotlin·Gradle 버전은 서로 맞아야 합니다(AGP 9.4는 Gradle 9.6 이상).
- C++/NDK 코드를 넣지 않습니다. 네이티브 빌드가 필요한 라이브러리는 피합니다.
- 화면은 크롬 확장 팝업과 같은 문구·동작을 따릅니다. 각 파일 머리 주석에 대응하는 확장·웹앱 파일이 적혀 있습니다.
- 서버 요청은 `data/Api.kt`의 `call`/`authed`/`get`/`download`로만 보냅니다(모바일 헤더와 401 처리).
- 화면 상태는 `MainViewModel`에, 탭 안의 입력은 `rememberSaveable`에 둡니다(탭을 오가도 유지).
