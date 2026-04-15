package com.playwright.example.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.playwright.example.common.BasePage;

public class LoginPage extends BasePage {

    /** 아이디 입력란 */
    private final Locator idField;

    /** 패스워드 입력란 */
    private final Locator pwField;

    /** 로그인 버튼 */
    private final Locator loginBtn;

    /** 로그인 실패 안내 텍스트 */
    private final Locator loginFailedTxt;

    public LoginPage(Page page) {
        super(page);
        idField = page.locator("input[name=\"username\"]");
        pwField = page.locator("input[name=\"password\"]");
        loginBtn = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("로그인"));
        loginFailedTxt = page.getByText("아이디 또는 패스워드가 일치하지 않습니다");
    }

    // ID, PW 입력 후 로그인 버튼 클릭
    public void login(String id, String pw) {
        fill(idField, id);
        fill(pwField, pw);
        click(loginBtn);
    }

    // 로그인 버튼 노출 여부 확인
    public boolean isLoginBtnDisplayed() {
        return isDisplayedWithTimeout(loginBtn, 3000);
    }

    // 로그인 실패 텍스트 노출 여부 확인
    public boolean isLoginFailedTxtDisplayed() {
        return isDisplayedWithTimeout(loginFailedTxt, 5000);
    }
}
