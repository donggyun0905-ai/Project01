package com.dmart.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// API_명세.md 1.7 참고. 세션 쿠키(JSESSIONID)를 쓰기 때문에 Allow-Origin은 * 대신
// 프론트 실제 주소를 명시해야 하고 Allow-Credentials도 true여야 함.
//
// @WebFilter 어노테이션을 안 쓰고 WEB-INF/web.xml에서 등록/매핑함 — AuthFilter보다
// 반드시 먼저 실행돼야 하는데(401 응답에도 CORS 헤더가 붙어야 브라우저가 읽을 수 있음),
// 어노테이션만 쓰면 여러 필터의 실행 순서가 스펙상 보장되지 않기 때문.
public class CorsFilter implements Filter {

    // TODO: 발표 환경 프론트 주소가 정해지면 갱신 (지금은 로컬 개발 주소, API_명세.md 상단 "임시값" 참고)
    private static final String ALLOWED_ORIGIN = "http://localhost:5500";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        resp.setHeader("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        chain.doFilter(request, response);
    }
}
