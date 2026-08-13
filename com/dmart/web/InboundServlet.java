package com.dmart.web;

import com.dmart.service.InboundService;
import com.dmart.util.ApiResponse;
import com.dmart.util.AuthUtil;
import com.dmart.util.JsonUtil;
import com.dmart.util.RequestUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

// API_명세.md 6.1 참고. InboundService를 그대로 감싸는 얇은 서블릿 —
// 검증/트랜잭션/재고초과 알림 로직은 전부 서비스 계층에 있고, 여기서는 입출력 변환과
// 서비스가 던지는 예외를 HTTP 상태코드로 매핑하는 역할만 한다.
@WebServlet("/api/inbound")
public class InboundServlet extends HttpServlet {

    private final InboundService inboundService = new InboundService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }

        Long itemId = RequestUtil.toLong(body.get("itemId"));
        Long zoneId = RequestUtil.toLong(body.get("zoneId"));
        Long partnerId = RequestUtil.toLong(body.get("partnerId"));
        Integer quantity = RequestUtil.toInteger(body.get("quantity"));
        String inboundDateStr = (String) body.get("inboundDate");
        if (itemId == null || zoneId == null || partnerId == null || quantity == null || inboundDateStr == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR",
                    "itemId, zoneId, partnerId, quantity, inboundDate는 필수입니다");
            return;
        }

        LocalDate inboundDate;
        try {
            inboundDate = LocalDate.parse(inboundDateStr);
        } catch (DateTimeParseException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "inboundDate 형식이 올바르지 않습니다 (yyyy-MM-dd)");
            return;
        }

        Long createdBy = AuthUtil.getUserId(req);
        try {
            InboundService.InboundResult result =
                    inboundService.inbound(itemId, zoneId, partnerId, quantity, inboundDate, createdBy);
            ApiResponse.success(resp, 201, JsonUtil.object(
                    "lotId", result.lotId,
                    "expiryDate", result.expiryDate,
                    "alertCreated", result.alertCreated
            ));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("입고 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }
}
