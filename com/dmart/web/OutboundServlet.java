package com.dmart.web;

import com.dmart.dto.StockLot;
import com.dmart.service.OutboundService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// API_명세.md 7번 참고. POST /api/outbound(7.1)와 GET /api/outbound/recommend(7.2)를
// 하나의 서블릿에서 pathInfo로 나눠 처리 — 둘 다 "출고"라는 같은 리소스에 대한 동작이라
// InboundServlet처럼 별도 서블릿으로 쪼개지 않았다.
@WebServlet("/api/outbound/*")
public class OutboundServlet extends HttpServlet {

    private final OutboundService outboundService = new OutboundService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (!"/recommend".equals(pathInfo)) {
            ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다");
            return;
        }

        Long itemId = parseLongParam(req.getParameter("itemId"));
        Integer quantity = parseIntParam(req.getParameter("quantity"));
        if (itemId == null || quantity == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "itemId와 quantity는 필수입니다");
            return;
        }
        String sortBy = req.getParameter("sortBy"); // fifo|fefo, 기본값은 서비스 내부에서 fefo로 처리

        try {
            OutboundService.RecommendResult result = outboundService.recommend(itemId, quantity, sortBy);
            List<Object> lots = result.lots.stream().map(OutboundServlet::lotToJson).collect(Collectors.toList());
            ApiResponse.success(resp, 200, JsonUtil.object(
                    "sortBy", result.sortBy,
                    "requestedQuantity", result.requestedQuantity,
                    "sufficient", result.sufficient,
                    "lots", lots
            ));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("출고 추천 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && !"/".equals(pathInfo)) {
            ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다");
            return;
        }

        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }

        Long lotId = RequestUtil.toLong(body.get("lotId"));
        Long partnerId = RequestUtil.toLong(body.get("partnerId"));
        Integer quantity = RequestUtil.toInteger(body.get("quantity"));
        String outboundDateStr = (String) body.get("outboundDate");
        if (lotId == null || partnerId == null || quantity == null || outboundDateStr == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR",
                    "lotId, partnerId, quantity, outboundDate는 필수입니다");
            return;
        }

        LocalDate outboundDate;
        try {
            outboundDate = LocalDate.parse(outboundDateStr);
        } catch (DateTimeParseException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "outboundDate 형식이 올바르지 않습니다 (yyyy-MM-dd)");
            return;
        }

        Long createdBy = AuthUtil.getUserId(req);
        try {
            OutboundService.OutboundResult result =
                    outboundService.outbound(lotId, partnerId, quantity, outboundDate, createdBy);
            ApiResponse.success(resp, 201, JsonUtil.object(
                    "outboundId", result.outboundId,
                    "remainingQuantity", result.remainingQuantity,
                    "alertCreated", result.alertCreated,
                    "approvalId", result.approvalId
            ));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("출고 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private static Map<String, Object> lotToJson(StockLot lot) {
        return JsonUtil.object(
                "lotId", lot.getLotId(),
                "quantity", lot.getQuantity(),
                "inboundDate", lot.getInboundDate(),
                "expiryDate", lot.getExpiryDate()
        );
    }

    private Long parseLongParam(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntParam(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
