package com.dmart.web;

import com.dmart.util.ApiResponse;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

// API_명세.md 1.3/1.4 참고 — /api/* 전체에 로그인 여부를 검사한다.
// 로그인 자체(/api/login)는 아직 로그인 안 한 상태로 호출돼야 하니 예외로 둔다.
// WEB-INF/web.xml에서 CorsFilter 다음 순서로 등록됨(순서 중요 — CorsFilter.java 주석 참고).
public class AuthFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/api/login");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // OPTIONS preflight는 CorsFilter가 이미 응답을 끝내버리므로 여기까지 안 옴 - 그래도 방어적으로 둠.
        if ("OPTIONS".equalsIgnoreCase(req.getMethod()) || PUBLIC_PATHS.contains(req.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            ApiResponse.error(resp, 401, "UNAUTHORIZED", "로그인이 필요합니다");
            return;
        }

        chain.doFilter(request, response);
    }
}
