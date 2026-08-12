package com.dmart.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

// API_명세.md 1.6 참고. 목록 조회 엔드포인트마다 반복되는 page/size 파싱 + 응답 포맷 조립을 여기 한 곳에 모음.
public class Pagination {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;

    public final int page;
    public final int size;
    public final int offset;

    private Pagination(int page, int size) {
        this.page = page;
        this.size = size;
        this.offset = (page - 1) * size;
    }

    public static Pagination from(HttpServletRequest req) {
        int page = parseIntOrDefault(req.getParameter("page"), 1);
        int size = parseIntOrDefault(req.getParameter("size"), DEFAULT_SIZE);
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
        return new Pagination(page, size);
    }

    public Map<String, Object> wrap(List<?> items, int total) {
        return JsonUtil.object("items", items, "page", page, "size", size, "total", total);
    }

    private static int parseIntOrDefault(String s, int defaultValue) {
        if (s == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
