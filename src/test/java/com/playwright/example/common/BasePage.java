package com.playwright.example.common;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class BasePage {

    /**
     * 변수 및 생성자
     */
    protected Page page;
    private static final String[] GNB_TABS = {"A", "B", "C", "D"};
    public static final String[] ALERT_POPUP_TEXTS = {
            "일시적인 오류", "네트워크 오류", "오류가 발생했습니다",
            "주문 마감시간이 지났습니다", "이미 장바구니에 담은 상품입니다"
    };

    public BasePage(Page page) {
        this.page = page;
    }

    /**
     * 기본 액션 (클릭, 입력, 노출 확인, 텍스트 추출 등)
     */
    public void click(Locator locator) {
        try {
            locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            locator.click();
        } catch (Exception e) {
            log.warn(">>> [BasePage] 클릭 실패: {}", e.getMessage());
            throw e;
        }
    }

    public void fill(Locator locator, String text) {
        try {
            locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            locator.fill(text);
        } catch (Exception e) {
            log.warn(">>> [BasePage] 텍스트 입력 실패: {}", e.getMessage());
            throw e;
        }
    }

    public boolean isDisplayed(Locator locator) {
        try {
            locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            return locator.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 요소가 지정된 시간 내에 화면에 노출되는지 확인합니다.
     * @param locator 확인할 요소
     * @param timeoutMs 대기 시간 (밀리초)
     */
    public boolean isDisplayedWithTimeout(Locator locator, int timeoutMs) {
        try {
            locator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(timeoutMs));
            return locator.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    public String getText(Locator locator) {
        try {
            locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            return locator.innerText().trim();
        } catch (Exception e) {
            log.warn(">>> [BasePage] 텍스트 추출 실패: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 대기 및 네비게이션 (로딩 대기, 뒤로 가기, 새로고침)
     */
    public void waitForPageLoad() {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (Exception e) {
            log.warn(">>> [BasePage] 페이지 로딩 대기 중 타임아웃 발생: {}", e.getMessage());
        }
    }

    public void goBack() {
        try {
            log.info(">>> [BasePage] 브라우저 뒤로가기를 수행합니다.");
            page.goBack(new Page.GoBackOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            waitForPageLoad();
        } catch (Exception e) {
            log.warn(">>> [BasePage] 뒤로가기 실패: {}", e.getMessage());
            throw e;
        }
    }

    public void reload() {
        page.reload(new Page.ReloadOptions().
                setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)); // 네트워크 중단 오류 방어
        waitForPageLoad();
    }

    /**
     * 탭 제어 (GNB / Bottom Tab)
     */
    public void clickGnbTab(String tabName) {
        Locator tabLocator = page.getByRole(
                com.microsoft.playwright.options.AriaRole.LINK,
                new Page.GetByRoleOptions().setName(tabName)
        );
        click(tabLocator);
        page.waitForTimeout(1000);
        waitForPageLoad();
    }

    /**
     * 현재 페이지의 데이터 갱신을 위해 다른 탭으로 이동했다가 다시 복귀합니다.
     * @param intermediateTab 갱신을 위해 이동할 탭
     * @param targetTab 복귀할 탭
     */
    public void refreshPageByTabSwitching(String intermediateTab, String targetTab) {
        log.debug(">>> [BasePage] 데이터 갱신을 위해 탭 스위칭 수행: {} -> {}", intermediateTab, targetTab);

        // 1. 중간 경유지 탭 클릭 (예: "MY")
        // GNB 메뉴는 보통 LINK나 BUTTON이므로 텍스트로 유연하게 찾습니다.
        page.getByText(intermediateTab).first().click();
        waitForPageLoad();

        // 2. 목적지 탭 클릭 (예: "AA")
        page.getByText(targetTab).first().click();
        waitForPageLoad();
    }

    public boolean isGnbTabDisplayed(String tabName) {
        Locator tabLocator = page.getByRole(
                com.microsoft.playwright.options.AriaRole.LINK,
                new Page.GetByRoleOptions().setName(tabName)
        );
        return isDisplayedWithTimeout(tabLocator, 5000);
    }

    public boolean isAllGnbTabsDisplayed() {
        for (String tabName : GNB_TABS) {
            if (!isGnbTabDisplayed(tabName)) {
                log.warn(">>> [BasePage] GNB 탭 미노출: {}", tabName);
                return false;
            }
        }
        return true;
    }

    /**
     * 유틸리티 (텍스트 추출, 팝업 닫기)
     */
    /**
     * 화면에 특정 텍스트가 포함된 알림 팝업이 노출되면 스크린샷을 찍고 '확인' 버튼을 클릭하여 닫습니다.
     * 브라우저 네이티브 dialog(alert/confirm)가 아닌 화면에 렌더링된 커스텀 팝업을 대상으로 합니다.
     * @param popupTexts 감지할 팝업 텍스트 목록 (예: "일시적인 오류", "네트워크 오류")
     */
    public void dismissAlertPopupIfPresent(String... popupTexts) {
        for (String popupText : popupTexts) {
            Locator popup = page.getByText(popupText).first();
            try {
                if (!isDisplayedWithTimeout(popup, 1000)) continue;

                // 팝업 감지 시 스크린샷 첨부
                log.warn(">>> [BasePage] 오류 팝업 감지: '{}'", popupText);
                try {
                    byte[] screenshot = page.screenshot();
                    byte[] resized = ScreenshotListener.resizeScreenshot(screenshot, 0.8f);
                    io.qameta.allure.Allure.addAttachment(
                            "오류 팝업 감지 - " + popupText,
                            "image/png",
                            new java.io.ByteArrayInputStream(resized),
                            "png"
                    );
                } catch (Exception screenshotEx) {
                    log.warn(">>> [BasePage] 팝업 스크린샷 첨부 실패: {}", screenshotEx.getMessage());
                }

                // '확인' 버튼 클릭하여 팝업 닫기
                Locator confirmBtn = page.getByRole(
                        com.microsoft.playwright.options.AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("확인").setExact(true));
                click(confirmBtn);
                log.warn(">>> [BasePage] 오류 팝업 '확인' 버튼 클릭하여 닫음");
                page.waitForTimeout(1000);
                return; // 팝업 하나 처리 후 종료
            } catch (Exception e) {
                // 해당 텍스트의 팝업이 없는 경우 — 다음 텍스트 확인
            }
        }
    }

    /**
     * 다수의 Locator 요소에서 텍스트만 추출하여 String 리스트로 반환합니다.
     * Playwright의 내장 기능인 allInnerTexts()를 활용하여 매우 빠르게 추출합니다.
     */
    protected List<String> getTextsFromLocators(Locator locators) {
        try {
            return locators.allInnerTexts().stream()
                    .map(String::trim)
                    .filter(text -> !text.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn(">>> [BasePage] 요소에서 텍스트를 추출할 수 없습니다. (빈 리스트 반환)");
            return java.util.Collections.emptyList();
        }
    }
}