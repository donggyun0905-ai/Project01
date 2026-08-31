package com.dmart.swing;

import com.dmart.dto.AppUser;

// 로그인한 사용자 정보를 앱 전체에서 들고 있는 자리. 웹 버전의 세션 쿠키 역할을 대신한다
// (서블릿이 없으니 HttpSession도 없어서, 그냥 로그인 성공 시 한 번 담아두고 계속 참조).
public class Session {

    private static AppUser currentUser;

    public static void login(AppUser user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static AppUser getUser() {
        return currentUser;
    }

    public static Long getUserId() {
        return currentUser != null ? currentUser.getUserId() : null;
    }

    public static boolean isAdmin() {
        return currentUser != null && "ADMIN".equals(currentUser.getRole());
    }
}
