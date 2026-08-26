package com.dmart.web;

import com.dmart.dao.OutboundDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Outbound;
import com.dmart.dto.StockLot;
import com.dmart.service.OutboundService;
import com.dmart.util.ApiResponse;
import com.dmart.util.AuthUtil;
import com.dmart.util.JsonUtil;
import com.dmart.util.Pagination;
import com.dmart.util.RequestUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// API_명세.md 7번 참고. POST /api/outbound(7.1)와 GET /api/outbound/recommend(7.2)를
// 하나의 서블릿에서 pathInfo로 나눠 처리 — 둘 다 "출고"라는 같은 리소스에 대한 동작이라
// InboundServlet처럼 별도 서블릿으로 쪼개지 않았다.
// GET /api/outbound(pathInfo 없음)는 출고 이력 목록 - 나중에 outbound.html에서 보여주려고 추가함.
@WebServlet("/api/outbound/*")
public class OutboundServlet extends HttpServlet {

    private final OutboundService outboundService = new OutboundService();
    private final OutboundDao outboundDao = new OutboundDao();
    private final StockLotDao stockLotDao = new StockLotDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        if (pathInfo == null || "/".equals(pathInfo)) {
            doList(req, resp);
            return;
        }

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
            EventBus.publish("outbound");
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

    private void doList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long itemId = parseLongParam(req.getParameter("itemId"));
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<Outbound> list = outboundDao.findPage(conn, itemId, pg.offset, pg.size);
            int total = outboundDao.count(conn, itemId);
            List<Object> data = new ArrayList<>();
            for (Outbound outbound : list) {
                // 화면에서 품목명/구역을 보여주려면 itemId/zoneId가 필요한데 OUTBOUND 자체엔
                // 없어서 로트를 한 번 더 찾아 붙여준다 (ItemServlet의 totalQuantity와 같은 방식).
                StockLot lot = stockLotDao.findById(conn, outbound.getLotId());
                data.add(toJson(outbound, lot != null ? lot.getItemId() : null, lot != null ? lot.getZoneId() : null));
            }
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("출고 이력 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private static Map<String, Object> toJson(Outbound outbound, Long itemId, Long zoneId) {
        return JsonUtil.object(
                "outboundId", outbound.getOutboundId(),
                "lotId", outbound.getLotId(),
                "itemId", itemId,
                "zoneId", zoneId,
                "partnerId", outbound.getPartnerId(),
                "quantity", outbound.getQuantity(),
                "outboundDate", outbound.getOutboundDate(),
                "createdBy", outbound.getCreatedBy()
        );
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
