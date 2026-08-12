package com.dmart.util;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

// API_명세.md 1.2 공통 응답 포맷({success,data} / {success:false,error}) 작성용 헬퍼.
public class ApiResponse {

    public static void success(HttpServletResponse resp, int status, Object data) throws IOException {
        write(resp, status, JsonUtil.object("success", true, "data", data));
    }

    public static void error(HttpServletResponse resp, int status, String code, String message) throws IOException {
        write(resp, status, JsonUtil.object(
                "success", false,
                "error", JsonUtil.object("code", code, "message", message)
        ));
    }

    private static void write(HttpServletResponse resp, int status, Map<String, Object> body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(JsonUtil.toJson(body));
    }
}
