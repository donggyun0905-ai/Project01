package com.dmart.web;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ApprovalDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Approval;
import com.dmart.service.ApprovalService;
import com.dmart.util.ApiResponse;
import com.dmart.util.AuthUtil;
import com.dmart.util.JsonUtil;
import com.dmart.util.Pagination;
import com.dmart.util.RequestUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// API_명세.md 12번 참고. GET/POST는 로그인이면 되고, PATCH(승인/반려)는 ADMIN만.
// AlertServlet과 동일하게 PATCH는 service()에서 따로 분기.
@WebServlet("/api/approvals/*")
public class ApprovalServlet extends HttpServlet {

    private final ApprovalDao approvalDao = new ApprovalDao();
    private final AlertDao alertDao = new AlertDao();
    private final ApprovalService approvalService = new ApprovalService();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String status = req.getParameter("status");
        String requestType = req.getParameter("requestType");
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<Approval> approvals = approvalDao.findPage(conn, status, requestType, pg.offset, pg.size);
            int total = approvalDao.count(conn, status, requestType);
            List<Object> data = new java.util.ArrayList<>();
            for (Approval approval : approvals) {
                data.add(toJson(conn, approval));
            }
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("승인 목록 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

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
        String requestType = (String) body.get("requestType");
        Integer requestedQty = RequestUtil.toInteger(body.get("requestedQty"));
        Long partnerId = RequestUtil.toLong(body.get("partnerId"));
        if (itemId == null || requestType == null || requestedQty == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "itemId, requestType, requestedQty는 필수입니다");
            return;
        }
        Long requestedBy = AuthUtil.getUserId(req);

        try {
            // alertId는 시스템 자동 제안(6·7·9번)에서만 채워짐 — 수동 요청 경로인 여기선 항상 null
            Long approvalId = approvalService.create(itemId, null, requestType, requestedQty, partnerId, requestedBy);
            EventBus.publish("approval");
            try (Connection conn = DBConnection.getConnection()) {
                Approval approval = approvalDao.findById(conn, approvalId);
                ApiResponse.success(resp, 201, toJson(conn, approval));
            }
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("승인 요청 생성 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long approvalId = parseId(req.getPathInfo());
        if (approvalId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 approvalId입니다");
            return;
        }

        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }
        String status = (String) body.get("status");
        Long approvedBy = AuthUtil.getUserId(req);

        try {
            ApprovalService.DecisionResult result = approvalService.decide(approvalId, status, approvedBy);
            EventBus.publish("approval");
            // 승인 처리 결과로 실제 입고/출고가 자동 실행됐으면(발주/출고 자동실행), 입고·출고
            // 화면도 자기 데이터가 바뀐 걸 알아야 하니 그 화면이 구독하는 종류도 같이 보낸다.
            if ("inbound".equals(result.executedService)) {
                EventBus.publish("inbound");
            } else if ("outbound".equals(result.executedService)) {
                EventBus.publish("outbound");
                // 출고 승인 처리 중 부족분을 자동으로 먼저 채워 넣었으면(이상출고 하이브리드 -
                // ApprovalService.tryAutoReplenish) 입고 데이터도 같이 바뀐 것이므로 그것도 알린다.
                if (result.shortageApprovalId != null) {
                    EventBus.publish("inbound");
                }
            }
            ApiResponse.success(resp, 200, toJson(result));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("승인 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
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

    // alertType: alertId가 있으면 그 알림의 종류("이상출고"/"재고부족" 등)를 같이 내려준다 -
    // 화면에서 requestType="출고"인데 alertType="이상출고"인 걸 구분해서 "이상출고"로 보여주기 위함
    // (AutoManageService.isAbnormalOutboundDerived와 같은 판단 기준).
    private Map<String, Object> toJson(Connection conn, Approval approval) throws SQLException {
        String alertType = null;
        if (approval.getAlertId() != null) {
            Alert alert = alertDao.findById(conn, approval.getAlertId());
            alertType = alert != null ? alert.getAlertType() : null;
        }
        return JsonUtil.object(
                "approvalId", approval.getApprovalId(),
                "itemId", approval.getItemId(),
                "alertId", approval.getAlertId(),
                "alertType", alertType,
                "requestType", approval.getRequestType(),
                "requestedQty", approval.getRequestedQty(),
                "partnerId", approval.getPartnerId(),
                "status", approval.getStatus(),
                "requestedBy", approval.getRequestedBy(),
                "requestedAt", approval.getRequestedAt(),
                "approvedBy", approval.getApprovedBy(),
                "approvedAt", approval.getApprovedAt(),
                "fulfilledQty", approval.getFulfilledQty()
        );
    }

    // 12번 PATCH 응답 — 반려/발주실행/출고실행마다 필드 구성이 달라서 object() 대신 직접 조립.
    private static Map<String, Object> toJson(ApprovalService.DecisionResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("approvalId", result.approvalId);
        data.put("status", result.status);
        if (result.executedService != null) {
            data.put("executedService", result.executedService);
            data.put("executionFailed", result.executionFailed);
            if ("outbound".equals(result.executedService)) {
                data.put("requestedQty", result.requestedQty);
                data.put("fulfilledQty", result.fulfilledQty);
                if (result.shortageApprovalId != null) {
                    data.put("shortageApprovalId", result.shortageApprovalId);
                }
            }
            if (Boolean.TRUE.equals(result.executionFailed)) {
                data.put("executionError", result.executionError);
            } else if ("inbound".equals(result.executedService)) {
                data.put("resultLotIds", result.resultLotIds);
            } else if ("outbound".equals(result.executedService)) {
                data.put("resultOutboundIds", result.resultOutboundIds);
            }
        }
        return data;
    }
}
