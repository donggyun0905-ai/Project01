package com.dmart.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * 서블릿에서 로그인한 사용자 정보를 꺼내거나 권한(ADMIN)을 확인할 때 쓰는 헬퍼.
 * AuthFilter가 이미 "로그인 여부"는 걸러주므로, 여기 있는 getter들은 이 헬퍼가 호출되는
 * 시점엔 세션이 항상 있다고 가정한다 (AuthFilter 뒤에서만 호출되는 서블릿 안에서 쓰는 용도).
 */
public class AuthUtil {

    public static Long getUserId(HttpServletRequest req) {
        return (Long) req.getSession().getAttribute("userId");
    }

    public static String getRole(HttpServletRequest req) {
        return (String) req.getSession().getAttribute("role");
    }

    public static boolean isAdmin(HttpServletRequest req) {
        return "ADMIN".equals(getRole(req));
    }

    /**
     * ADMIN이 아니면 403 응답을 쓰고 false를 반환한다 — 호출하는 서블릿은
     * {@code if (!AuthUtil.requireAdmin(req, resp)) return;} 형태로 쓰면 됨.
     */
    public static boolean requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!isAdmin(req)) {
            ApiResponse.error(resp, 403, "FORBIDDEN", "관리자만 접근할 수 있습니다");
            return false;
        }
        return true;
    }
}
