package com.dmart.web;

import com.dmart.dao.StockLotDao;
import com.dmart.dao.UserWarehouseDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.StockLot;
import com.dmart.dto.UserWarehouse;
import com.dmart.service.StockLotAdjustmentService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// API_명세.md 10번 참고. 10.1(목록)/10.2(관리자 직접수정)만 구현 — 10.3/10.4(삭제/복원)는
// STOCK_CHANGE_LOG의 lot_id FK(RESTRICT)와 "로트 삭제 + 그 삭제를 감사로그로 남김"이
// 스키마상 동시에 성립할 수 없어서 스키마 결정 후 별도로 진행하기로 함.
@WebServlet("/api/stock-lots/*")
public class StockLotServlet extends HttpServlet {

    private final StockLotDao stockLotDao = new StockLotDao();
    private final UserWarehouseDao userWarehouseDao = new UserWarehouseDao();
    private final StockLotAdjustmentService adjustmentService = new StockLotAdjustmentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long itemId = parseLongParam(req.getParameter("itemId"));
        Long zoneId = parseLongParam(req.getParameter("zoneId"));
        Long warehouseId = parseLongParam(req.getParameter("warehouseId"));
        String status = req.getParameter("status");
        if (status == null) {
            status = "NORMAL"; // 10.1 기본값
        }
        // 입고 이력 화면처럼 "실제 새로 입고된 로트만" 보고 싶을 때 씀 - 이동/반품폐기로 원본
        // 로트에서 분할되어 생긴 로트(parent_lot_id가 있는 로트)는 새 입고가 아니므로 제외.
        boolean originOnly = "true".equals(req.getParameter("originOnly"));
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            // STAFF는 배정 창고만 — 4번/10.1과 동일한 제약.
            List<Long> allowedWarehouseIds = null;
            if (!AuthUtil.isAdmin(req)) {
                allowedWarehouseIds = userWarehouseDao.findByUserId(conn, AuthUtil.getUserId(req)).stream()
                        .map(UserWarehouse::getWarehouseId)
                        .collect(Collectors.toList());
            }
            List<StockLot> lots = stockLotDao.findPage(conn, itemId, zoneId, warehouseId, status, allowedWarehouseIds, originOnly, pg.offset, pg.size);
            int total = stockLotDao.count(conn, itemId, zoneId, warehouseId, status, allowedWarehouseIds, originOnly);
            List<Object> data = lots.stream().map(StockLotServlet::toJson).collect(Collectors.toList());
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("재고 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long lotId = parseId(req.getPathInfo());
        if (lotId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 lotId입니다");
            return;
        }

        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }

        Integer quantity = RequestUtil.toInteger(body.get("quantity"));
        String status = (String) body.get("status");
        String reason = (String) body.get("reason");
        Long changedBy = AuthUtil.getUserId(req);

        try {
            StockLotAdjustmentService.AdjustResult result = adjustmentService.adjust(lotId, quantity, status, reason, changedBy);
            ApiResponse.success(resp, 200, JsonUtil.object(
                    "lotId", result.lotId,
                    "quantity", result.quantity,
                    "status", result.status
            ));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("재고 직접 수정 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long lotId = parseId(req.getPathInfo());
        if (lotId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 lotId입니다");
            return;
        }
        String reason = req.getParameter("reason"); // 10.3 참고 — 쿼리 파라미터로 전달

        try {
            Long resultLotId = adjustmentService.delete(lotId, reason, AuthUtil.getUserId(req));
            ApiResponse.success(resp, 200, JsonUtil.object("lotId", resultLotId));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("재고 삭제 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private static Map<String, Object> toJson(StockLot lot) {
        return JsonUtil.object(
                "lotId", lot.getLotId(),
                "itemId", lot.getItemId(),
                "zoneId", lot.getZoneId(),
                "partnerId", lot.getPartnerId(),
                "quantity", lot.getQuantity(),
                "initialQuantity", lot.getInitialQuantity(),
                "inboundDate", lot.getInboundDate(),
                "expiryDate", lot.getExpiryDate(),
                "status", lot.getStatus(),
                "parentLotId", lot.getParentLotId()
        );
    }

    private Long parseId(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        try {
            return Long.parseLong(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
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
}
