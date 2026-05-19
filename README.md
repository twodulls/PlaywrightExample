# PlaywrightExample

Java + Playwright 기반의 Web UI 자동화 테스트 프레임워크입니다.  
TestNG를 테스트 러너로 사용하고, Allure를 통해 리포트를 생성합니다.  
Chrome / Edge 멀티 브라우저 순차 실행과 브라우저별 Allure 리포트 분리를 지원합니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java |
| Build Tool | Gradle |
| Test Runner | TestNG 7.11 |
| UI Automation | Microsoft Playwright 1.58 |
| Report | Allure 2.24 |
| Assertion | AssertJ, Hamcrest |
| Test Data | YAML (SnakeYAML / Jackson YAML) |
| Logging | SLF4J + Logback |
| Utilities | Lombok, Apache Commons Lang3, Guava, Gson |

---

## 프로젝트 구조

```
src/test/
├── java/com/playwright/example/
│   ├── common/
│   │   ├── BaseTest.java          # 테스트 생명주기 관리 (Suite/Test/Class/Method)
│   │   └── BasePage.java          # 공통 UI 액션 (클릭, 입력, 텍스트 추출, 팝업 처리 등)
│   ├── listener/
│   │   ├── RetryAnalyzer.java     # 실패 테스트 자동 재시도 + 전체 @Test 메서드에 자동 주입
│   │   └── ScreenshotListener.java # 실패/스킵 시 Allure 스크린샷 자동 첨부
│   ├── page/
│   │   └── LoginPage.java         # 로그인 페이지 POM
│   ├── testdata/
│   │   ├── TestData.java          # 테스트 데이터 모델 (url, id, pw, headlessOption)
│   │   └── TestDataManager.java   # 환경별 YAML 파일 로드 (싱글톤)
│   ├── uitest/
│   │   └── LoginTest.java         # 로그인 UI 테스트
│   └── utils/
│       └── DataUtils.java         # 날짜/가격 텍스트 파싱 유틸
└── resources/
    ├── suite/suite.xml            # TestNG 스위트 설정 (브라우저 파라미터 포함)
    ├── yml-qa/testdata.yml        # QA 환경 테스트 데이터
    ├── categories.json            # Allure 결함 카테고리 정의
    ├── allure.properties          # Allure 설정
    └── logback.xml                # 로그 설정
```

> 멀티 브라우저(`multiBrowserTest`) 실행을 위해서는 `suite/` 하위에  
> `chrome-suite.xml`, `edge-suite.xml`을 각각 작성해야 합니다.  
> 각 스위트의 `<test>` 태그에 `<parameter name="browser" value="chrome|msedge"/>`를 지정하면,  
> `BaseTest`의 `@BeforeTest(@Parameters("browser"))`로 전달되어 해당 브라우저로 실행됩니다.

---

## 주요 설계

### Page Object Model (POM)
- `BasePage`에 공통 UI 액션(click, fill, isDisplayed 등)을 정의합니다.
- 각 페이지는 `BasePage`를 상속하고, 해당 페이지의 로케이터와 메서드만 포함합니다.

### Storage State를 이용한 로그인 최적화
- `@BeforeSuite`에서 최초 1회 로그인 후 쿠키/세션을 `build/storage-state.json`에 저장합니다.
- 이후 모든 테스트 클래스는 저장된 상태를 로드하여 로그인 과정을 생략합니다.
- 로그인 테스트(`LoginTest`)는 `isLoginRequired()`를 `false`로 오버라이드하여 자동 로그인을 건너뜁니다.
- 뷰포트 해상도는 `1440 x 900`으로 고정되어 컨텍스트 및 Allure 리포트에 모두 반영됩니다.

### 자동 재시도
- `RetryAnalyzer`는 `IRetryAnalyzer`와 `IAnnotationTransformer`를 함께 구현하여,
  `suite.xml`의 listener로 등록되면 모든 `@Test` 메서드에 자동으로 적용됩니다.
- 실패한 테스트를 최대 2회 자동 재시도합니다 (`MAX_RETRY_COUNT = 2`).
- 재시도 전 페이지 세션 유효성을 확인하고, 화면에 남은 알림 팝업을 선제적으로 닫습니다.

### 자동 스크린샷 첨부
- `ScreenshotListener`가 테스트 실패/스킵 시 Allure 리포트에 스크린샷을 자동으로 첨부합니다.
- 스크린샷은 60% 크기로 리사이즈되어 저장됩니다.
- 재시도 횟수와 타임스탬프를 포함한 이름으로 첨부됩니다
  (예: `실패 스크린샷 - 2회차 [2026-05-19 14:31:05]`, 최종 실패 시 `N회차 (최종)`).
- `BasePage.dismissAlertPopupIfPresent()`에서 오류 팝업이 감지되는 경우에도 별도 스크린샷이 첨부됩니다.

### 안정성 보강
- `BasePage.waitForPageLoad()`: `NETWORKIDLE` 대기 시 폴링 앱으로 인한 풀타임아웃(30초)을 피하기 위해 5초로 제한하고, 초과해도 무시하고 진행합니다.
- `BaseTest.globalSetup()`: 병렬 실행 시 동시 navigate로 인한 리소스 경합을 고려해 페이지 진입 타임아웃을 60초로 상향했습니다.

### Allure 카테고리 자동 분류
`categories.json`에 정의된 패턴으로 실패 원인을 자동 분류합니다.

| 카테고리 | 대상 상태 | 매칭 패턴 |
|----------|----------|-----------|
| 검증 실패 (앱 결함) | failed | AssertionError |
| UI 요소 미노출 | broken | TimeoutError, Locator |
| 페이지 로딩 타임아웃 | broken | Timeout |
| 테스트 데이터 부재 | skipped | SkipException |
| 테스트 코드 오류 | broken | NullPointerException, RuntimeException |
| 앱 서버 오류 | broken | Navigation, net::, ERR_ |

---

## 환경 설정

### 테스트 데이터 (YAML)
환경별 YAML 파일에 URL, 계정, 헤드리스 옵션을 설정합니다.

```
src/test/resources/yml-{env}/testdata.yml
```

```yaml
url: https://www.test.com
headlessOption: false   # true: 헤드리스 모드, false: 브라우저 표시
id: userID
pw: userPassword
```

새로운 환경을 추가하려면 `yml-{환경명}/testdata.yml` 파일을 생성합니다.  
예) `yml-dev/testdata.yml`, `yml-it/testdata.yml`

---

## 실행 방법

### 전체 테스트 실행 (기본: qa 환경)
```bash
./gradlew test
```

### 환경 지정 실행
```bash
./gradlew test -Denv=dev
./gradlew test -Denv=it
```

### 스위트(suite.xml) 기반 실행
`-Psmoke_test` 프로퍼티를 부여하면 `src/test/resources/suite/chrome-suite.xml`을 스위트로 사용합니다.

```bash
./gradlew test -Psmoke_test
```

### 브라우저 지정 실행
브라우저는 두 가지 방법으로 지정할 수 있습니다.

1. **TestNG 스위트 파라미터**: `suite.xml`의 `<test>` 태그에 `browser` 파라미터를 설정합니다. (권장)
2. **시스템 프로퍼티**: Storage State 최초 로그인 시 사용되는 브라우저를 지정합니다.

```bash
./gradlew test -Dbrowser=msedge
```

> `BaseTest`는 `@BeforeTest(@Parameters("browser"))`로 스위트 파라미터를 우선 사용하며,  
> 값이 없으면 기본값 `chrome`을 사용합니다.

### Allure 리포트 생성 및 열기
```bash
# 보고서 생성 후 브라우저에서 열기
./gradlew allureServe

# 보고서 파일 생성 (build/reports/allure-report/allureReport-{날짜}/)
./gradlew allureReport
```

> Allure 리포트는 실행할 때마다 날짜가 포함된 폴더명으로 생성되며, trend 누적을 위해 history가 자동으로 관리됩니다.

---

## 멀티 브라우저 테스트 (Chrome → Edge 순차 실행)

`build.gradle`에 정의된 `multiBrowserTest` 태스크는 다음 순서로 자동 실행됩니다.

1. `cleanAllureResults_chrome` → `testChrome` → `allureReport_chrome`
2. `cleanAllureResults_edge` → `testEdge` → `allureReport_edge`

```bash
./gradlew multiBrowserTest --continue
```

- `--continue` 플래그를 사용하면 Chrome 테스트 일부 실패 시에도 Edge 테스트를 계속 진행합니다.
- 브라우저별로 결과 디렉토리가 분리됩니다.

| 항목 | Chrome | Edge |
|------|--------|------|
| 스위트 XML | `suite/chrome-suite.xml` | `suite/edge-suite.xml` |
| 결과 디렉토리 | `build/allure-results-chrome/` | `build/allure-results-edge/` |
| 리포트 디렉토리 | `build/reports/allure-report/chrome-{날짜}/` | `build/reports/allure-report/edge-{날짜}/` |

브라우저별 단독 실행도 가능합니다.

```bash
./gradlew testChrome          # Chrome 단독 테스트
./gradlew testEdge            # Edge 단독 테스트
./gradlew allureReport_chrome # Chrome 리포트만 재생성
./gradlew allureReport_edge   # Edge 리포트만 재생성
```

> Trend 누적: 각 브라우저의 리포트 생성 후 `history` 폴더가 자동으로 `allure-results-{browser}`에 복사되어  
> 다음 실행 시 Trend 그래프가 누적 표시됩니다.

---

## 병렬 실행

`build.gradle`에서 병렬 실행 설정을 변경할 수 있습니다.

```groovy
test {
    useTestNG() {
        options.parallel = 'tests'  // tests / classes / methods
        options.threadCount = 4     // 병렬 스레드 수
    }
}
```

기본값은 `parallel = 'tests'`, `threadCount = 4` 이며, `testChrome` / `testEdge` 태스크에도 동일하게 적용되어 있습니다.

---

## Playwright CLI

`build.gradle`에 등록된 `playwright` 태스크를 통해 Playwright CLI를 직접 실행할 수 있습니다.

```groovy
// build.gradle
tasks.register('playwright', JavaExec) {
    classpath sourceSets.test.runtimeClasspath
    mainClass = 'com.microsoft.playwright.CLI'
}
```

`sourceSets.test.runtimeClasspath`를 classpath로 지정하기 때문에 별도 설치 없이  
Gradle 의존성에 선언된 Playwright 버전의 CLI를 그대로 사용합니다.

### codegen — 로케이터 자동 생성

브라우저를 열고 직접 요소를 클릭하면, 해당 요소에 대한 Playwright 로케이터 코드를 자동으로 생성해 줍니다.  
Page Object 작성 시 로케이터를 직접 찾지 않아도 되어 개발 생산성을 높여줍니다.

```bash
# 특정 URL로 codegen 실행
./gradlew playwright --args="codegen https://www.test.com"

# 특정 언어(Java)로 코드 생성
./gradlew playwright --args="codegen --target java https://www.test.com"
```

실행하면 두 개의 창이 열립니다.
- **브라우저 창**: 실제 사이트를 조작하는 창
- **Playwright Inspector**: 조작에 따라 실시간으로 생성되는 코드를 확인하는 창

생성된 코드를 그대로 Page Object 클래스에 붙여넣어 로케이터로 활용할 수 있습니다.

```bash
# 전체 명령어 목록 확인
./gradlew playwright --args="help"
```
