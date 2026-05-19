package com.playwright.example.uitest;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import com.playwright.example.common.BaseTest;
import com.playwright.example.page.LoginPage;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static io.qameta.allure.Allure.step;

@Epic("Product Name-Web Smoke Test")
@Feature("로그인")
@Slf4j
public class LoginTest extends BaseTest {
    private LoginPage loginPage;
    private SoftAssertions sa;

    // LoginTest는 BaseTest의 자동 로그인을 실행하지않음
    @Override
    protected boolean isLoginRequired() {return false;}

    @BeforeClass
    public void beforeClass() {
        loginPage = new LoginPage(page);
    }

    @BeforeMethod
    public void beforeMethod() {
        sa = new SoftAssertions();
    }

    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 1)
    public void 유효하지않은_계정_로그인_불가_동작_확인() {
        step("[Given] 현재 로그인 상태라면 마이페이지로 이동하여 로그아웃 수행");
        if (!loginPage.isLoginBtnDisplayed()) {
            log.info(">>> [LoginTest] 이전 테스트로 인해 로그인 상태입니다. 로그아웃을 진행합니다.");
        }

        step("[When] 유효하지 않은 계정으로 로그인 시도");
        loginPage.login("qa_test_not_exist_999", "QaTest!@#1234");

        step("[Then] 로그인 실패 에러 메시지 노출 검증");
        sa.assertThat(loginPage.isLoginFailedTxtDisplayed())
                .as("로그인 실패 에러 메시지가 노출되지 않았습니다.")
                .isTrue();

        sa.assertAll();
    }

    @Severity(SeverityLevel.BLOCKER)
    @Test(priority = 2)
    public void 테스트_계정_로그인_및_홈_진입_확인() {
        step("[Given] 현재 로그인 상태라면 마이페이지로 이동하여 로그아웃 수행");
        if (!loginPage.isLoginBtnDisplayed()) {
            log.info(">>> [LoginTest] 이전 테스트로 인해 로그인 상태입니다. 로그아웃을 진행합니다.");
        }

        step("[When] 유효한 계정으로 로그인 시도 (아이디: '" + ID + "')");
        loginPage.login(ID, PW);


        sa.assertAll();
    }
}