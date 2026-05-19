package com.playwright.example.common;

import com.google.common.collect.ImmutableMap;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import com.playwright.example.page.LoginPage;
import com.playwright.example.testdata.TestData;
import com.playwright.example.testdata.TestDataManager;
import org.testng.ITestContext;
import org.testng.annotations.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.github.automatedowl.tools.AllureEnvironmentWriter.allureEnvironmentWriter;

@Slf4j
public abstract class BaseTest {
    public static String ENV;
    public static TestData testData;
    public static String ID, PW;

    public Browser browser;
    public Page page;
    public BasePage basePage;
    private Playwright playwright;
    private static boolean isHeadless;

    // Storage State 파일 경로: BeforeSuite에서 로그인 후 저장, 이후 재사용
    private static Path storageStatePath;

    // 뷰포트 해상도 상수: 브라우저 컨텍스트와 Allure 리포트에 모두 반영
    private static final int VIEWPORT_WIDTH  = 1440;
    private static final int VIEWPORT_HEIGHT = 900;

    /**
     * 기본적으로 모든 테스트는 자동 로그인이 필요하다고 설정합니다.
     * 로그인이 필요 없는 클래스(예: LoginTest)에서만 이 메서드를 오버라이드하여 false를 반환합니다.
     */
    protected boolean isLoginRequired() { return true; }

    // TestNG LifeCycle (Suite -> Test -> Class -> Method)
    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {
        String env = System.getProperty("env");
        // dev 혹은 it 환경으로 테스트 수행하기 위해서는 env(yml) 파일을 환경에 맞게 세팅해야 한다.
        if (env == null) env = "qa";
        log.debug(">>> [BaseTest] env: : {}", env);

        ENV = env;
        testData = TestDataManager.getInstance(env).getData();
        ID = testData.getId();
        PW = testData.getPw();
        isHeadless = testData.isHeadlessOption();

        // Storage State 파일 경로 설정
        storageStatePath = Paths.get(System.getProperty("user.dir"), "build", "storage-state.json");

        // Storage State 파일이 이미 존재하면 재로그인 생략 (Chrome 실행 결과 재사용)
        if (isLoginRequired()) {
            if (storageStatePath.toFile().exists()) {
                log.info(">>> [BaseTest] Storage State 파일이 이미 존재합니다. 재로그인을 생략합니다: {}", storageStatePath);
            } else {
                saveStorageState();
            }
        } else {
            log.info(">>> [BaseTest] 이 테스트 클래스는 자동 로그인을 건너뜁니다.");
        }
    }

    @BeforeTest(alwaysRun = true)
    @Parameters("browser")
    public void beforeTest(ITestContext ctx, @Optional("chrome") String browserName) throws MalformedURLException {
        String suiteName = ctx.getCurrentXmlTest().getSuite().getName();
        log.debug(">>> [BaseTest] suite name : {}, browser : {}", suiteName, browserName);

        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(isHeadless)
                        .setChannel(browserName)
                        .setSlowMo(100));

        // Storage State가 저장된 경우 컨텍스트에 로드 → 로그인 생략
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setViewportSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        if (isLoginRequired() && storageStatePath != null && storageStatePath.toFile().exists()) {
            contextOptions.setStorageStatePath(storageStatePath);
            log.info(">>> [BaseTest] Storage State 로드 완료 > 로그인 과정을 생략합니다.");
        }

        page = browser.newContext(contextOptions).newPage();
        basePage = new BasePage(page);

        // Allure 리포트 환경 정보
        // allure.results.directory JVM 인자로 전달된 경로를 우선 사용 (멀티 브라우저 실행 시 각 브라우저별 경로)
        // 지정되지 않은 경우 기본 경로 사용
        String allureResultsDir = System.getProperty("allure.results.directory",
                System.getProperty("user.dir") + "/build/allure-results");
        String osName = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String browserVersion = browser.version();
        String browserEngine = browser.browserType().name();

        allureEnvironmentWriter(
                ImmutableMap.<String, String>builder()
                        .put("Environment", ENV)
                        .put("Suite Name", suiteName)
                        .put("URL", testData.getUrl())
                        .put("OS", osName)
                        .put("Browser", browserName + " (" + browserEngine + ")")
                        .put("Browser Version", browserVersion)
                        .put("Viewport", VIEWPORT_WIDTH + " x " + VIEWPORT_HEIGHT)
                        .build(),
                allureResultsDir + "/");
    }

    @BeforeClass(alwaysRun = true)
    public void globalSetup() {
        log.info(">>> [BaseTest] 공통 URL 진입: {}", testData.getUrl());
        // 병렬 실행 시 여러 브라우저가 동시에 navigate할 때 리소스 경합으로 타임아웃 발생 가능
        // 기본 30초 → 60초로 상향하여 안정성 확보
        page.navigate(testData.getUrl(),
                new Page.NavigateOptions().setTimeout(60_000));
        page.waitForLoadState();
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeMethodBase() {
        // 모든 테스트 실행 전 알림 팝업이 떠있으면 자동으로 닫습니다.
        if (page != null && !page.isClosed()) {
            BasePage basePage = new BasePage(page);
            basePage.dismissAlertPopupIfPresent(BasePage.ALERT_POPUP_TEXTS);
        }
    }

    @AfterTest
    public void afterTest() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        log.info(">>> [BaseTest] 브라우저 및 Playwright 인스턴스를 종료합니다.");
    }

    /**
     * 최초 1회 로그인하여 쿠키와 세션을 Storage State 파일로 저장합니다.
     * 이후 모든 테스트 클래스는 이 파일을 로드하여 로그인 과정을 생략합니다.
     */
    private void saveStorageState() {
        log.info(">>> [BaseTest] Storage State 생성을 위한 최초 로그인을 수행합니다.");
        Playwright pw = Playwright.create();
        try {
            Browser tempBrowser = pw.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(isHeadless)
                            .setChannel(System.getProperty("browser", "chrome")));
            Page tempPage = tempBrowser.newContext(
                    new Browser.NewContextOptions().setViewportSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)).newPage();

            tempPage.navigate(testData.getUrl());
            tempPage.waitForLoadState();

            LoginPage loginPage = new LoginPage(tempPage);
            loginPage.login(ID, PW);

            // 쿠키와 세션이 완전히 설정될때까지 대기
            tempPage.waitForURL("**/@*/**"); // 프랜차이즈명이 포함된 경로 (예: https://sfn-qa.oesikup.com/@donkachun/all)
            tempPage.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE); // 네트워크 요청 완료

            // 로그인 완료 후, Storage State 파일경로에 쿠키와 세션 저장
            tempPage.context().storageState(
                    new com.microsoft.playwright.BrowserContext.StorageStateOptions()
                            .setPath(storageStatePath));
            log.info(">>> [BaseTest] Storage State 저장 완료: {}", storageStatePath);

            tempBrowser.close();
        } finally {
            pw.close();
        }
    }
}