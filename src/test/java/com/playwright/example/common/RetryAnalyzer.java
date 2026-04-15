package com.playwright.example.common;

import com.microsoft.playwright.Page;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

@Getter
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RetryAnalyzer.class);
    public static final int MAX_RETRY_COUNT = 2;
    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (retryCount < MAX_RETRY_COUNT) {
            // 재시도 전 페이지 세션이 유효한지 확인
            if (!isPageValid(result)) {
                log.warn(">>> [Retry] '{}' 페이지 세션이 유효하지 않아 재시도를 중단합니다.", result.getName());
                return false;
            }
            // 재시도 전 화면에 남아있는 알림 팝업 처리 (@BeforeMethod 실행 전 선제 대응)
            dismissPopupIfPresent(result);
            retryCount++;
            log.warn(">>> [Retry] '{}' 테스트 실패 - {}번째 재시도 중... (최대 {}회)",
                    result.getName(), retryCount, MAX_RETRY_COUNT);
            return true;
        }
        log.error(">>> [Retry] '{}' 테스트 최종 실패 - {}회 재시도 모두 실패",
                result.getName(), MAX_RETRY_COUNT);
        return false;
    }

    /**
     * 재시도 전 화면에 알림 팝업이 남아있으면 닫습니다.
     * @BeforeMethod의 dismissAlertPopupIfPresent()보다 먼저 실행되어 선제적으로 처리합니다.
     */
    private void dismissPopupIfPresent(ITestResult result) {
        try {
            Object testInstance = result.getInstance();
            if (!(testInstance instanceof BaseTest)) return;

            Page page = ((BaseTest) testInstance).page;
            if (page == null || page.isClosed()) return;

            for (String popupText : BasePage.ALERT_POPUP_TEXTS) {
                com.microsoft.playwright.Locator popup = page.getByText(popupText).first();
                try {
                    popup.waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                            .setTimeout(3000));
                    com.microsoft.playwright.Locator confirmBtn = page.getByRole(
                            com.microsoft.playwright.options.AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("확인").setExact(true));
                    confirmBtn.click();
                    log.warn(">>> [Retry] 재시도 전 알림 팝업 감지 및 닫음: '{}'", popupText);
                    page.waitForTimeout(1000);
                    return;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn(">>> [Retry] 팝업 처리 중 오류 (무시하고 재시도 진행): {}", e.getMessage());
        }
    }

    /**
     * Playwright Page 세션이 유효한지 확인합니다.
     * 페이지가 종료되었거나 null인 경우 false를 반환합니다.
     */
    private boolean isPageValid(ITestResult result) {
        try {
            Object testInstance = result.getInstance();
            if (!(testInstance instanceof BaseTest)) return false;

            Page page = ((BaseTest) testInstance).page;

            if (page == null) return false;
            return !page.isClosed();
        } catch (Exception e) {
            log.warn(">>> [Retry] 페이지 유효성 확인 중 오류: {}", e.getMessage());
            return false;
        }
    }
}