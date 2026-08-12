package com.dmart.util;

import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.stream.Collectors;

public class RequestUtil {

    public static String readBody(HttpServletRequest req) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            return reader.lines().collect(Collectors.joining());
        }
    }

    /** JSON 숫자는 JsonUtil.parse()에서 항상 Double로 오므로, 요청 바디에서 Integer로 꺼낼 때 씀 */
    public static Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    public static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
