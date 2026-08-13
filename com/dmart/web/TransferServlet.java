package com.dmart.web;

import com.dmart.service.TransferService;
import com.dmart.util.ApiResponse;
import com.dmart.util.JsonUtil;
import com.dmart.util.RequestUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

// API_명세.md 8번 참고. TransferService를 감싸는 얇은 서블릿 — InboundServlet과 동일한 구조.
// handlerId는 6/7번의 createdBy와 달리 세션이 아니라 요청 바디에서 받는다 (8번 요청 예시에 명시됨).
@WebServlet("/api/transfers")
public class TransferServlet extends HttpServlet {

    private final TransferService transferService = new TransferService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }

        Long lotId = RequestUtil.toLong(body.get("lotId"));
        Long fromZoneId = RequestUtil.toLong(body.get("fromZoneId"));
        Long toZoneId = RequestUtil.toLong(body.get("toZoneId"));
        Integer quantity = RequestUtil.toInteger(body.get("quantity"));
        Long handlerId = RequestUtil.toLong(body.get("handlerId"));
        if (lotId == null || fromZoneId == null || toZoneId == null || quantity == null || handlerId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR",
                    "lotId, fromZoneId, toZoneId, quantity, handlerId는 필수입니다");
            return;
        }

        try {
            TransferService.TransferResult result =
                    transferService.transfer(lotId, fromZoneId, toZoneId, quantity, handlerId);
            ApiResponse.success(resp, 201, JsonUtil.object(
                    "transferId", result.transferId,
                    "splitOccurred", result.splitOccurred,
                    "newLotId", result.newLotId
            ));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("재고이동 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }
}
