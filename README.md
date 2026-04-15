# PlaywrightExample

Java + Playwright 기반의 Web UI 자동화 테스트 프레임워크입니다.  
TestNG를 테스트 러너로 사용하고, Allure를 통해 리포트를 생성합니다.

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
├── java/org/playwright/example/
│   ├── common/
│   │   ├── BaseTest.java          # 테스트 생명주기 관리 (Suite/Test/Class/Method)
│   │   ├── BasePage.java          # 공통 UI 액션 (클릭, 입력, 텍스트 추출 등)
│   │   ├── RetryAnalyzer.java     # 실패 테스트 자동 재시도 (최대 2회)
│   │   ├── RetryTransformer.java  # RetryAnalyzer를 전체 메서드에 적용
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
    ├── suite/suite.xml            # TestNG 스위트 설정
    ├── yml-qa/testdata.yml        # QA 환경 테스트 데이터
    ├── categories.json            # Allure 결함 카테고리 정의
    ├── allure.properties          # Allure 설정
    └── logback.xml                # 로그 설정
```

---

## 주요 설계

### Page Object Model (POM)
- `BasePage`에 공통 UI 액션(click, fill, isDisplayed 등)을 정의합니다.
- 각 페이지는 `BasePage`를 상속하고, 해당 페이지의 로케이터와 메서드만 포함합니다.

### Storage State를 이용한 로그인 최적화
- `@BeforeSuite`에서 최초 1회 로그인 후 쿠키/세션을 `build/storage-state.json`에 저장합니다.
- 이후 모든 테스트 클래스는 저장된 상태를 로드하여 로그인 과정을 생략합니다.
- 로그인 테스트(`LoginTest`)는 `isLoginRequired()`를 `false`로 오버라이드하여 자동 로그인을 건너뜁니다.

### 자동 재시도
- `RetryAnalyzer`가 실패한 테스트를 최대 2회 자동 재시도합니다.
- 재시도 전 페이지 세션 유효성을 확인하고, 화면에 남은 알림 팝업을 선제적으로 닫습니다.

### 자동 스크린샷 첨부
- `ScreenshotListener`가 테스트 실패/스킵 시 Allure 리포트에 스크린샷을 자동으로 첨부합니다.
- 스크린샷은 60% 크기로 리사이즈되어 저장됩니다.
- 재시도 횟수를 포함한 이름으로 첨부됩니다 (예: `실패 스크린샷 - 2회차`).

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
```bash
./gradlew test -Psmoke_test
```

### 브라우저 지정 실행 (기본: chrome)
```bash
./gradlew test -Dbrowser=msedge
```

### Allure 리포트 생성 및 열기
```bash
# 보고서 생성 후 브라우저에서 열기
./gradlew allureServe

# 보고서 파일 생성 (build/reports/allure-report/allureReport-{날짜}/)
./gradlew allureReport
```

> Allure 리포트는 실행할 때마다 날짜가 포함된 폴더명으로 생성되며, trend 누적을 위해 history가 자동으로 관리됩니다.

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
