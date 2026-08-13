package com.dmart.web;

import com.dmart.dao.ApprovalDao;
import com.dmart.db.DBConnection;
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
import java.util.stream.Collectors;

// API_명세.md 12번 참고. GET/POST는 로그인이면 되고, PATCH(승인/반려)는 ADMIN만.
// AlertServlet과 동일하게 PATCH는 service()에서 따로 분기.
@WebServlet("/api/approvals/*")
public class ApprovalServlet extends HttpServlet {

    private final ApprovalDao approvalDao = new ApprovalDao();
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
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<Approval> approvals = approvalDao.findPage(conn, status, pg.offset, pg.size);
            int total = approvalDao.count(conn, status);
            List<Object> data = approvals.stream().map(ApprovalServlet::toJson).collect(Collectors.toList());
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
            try (Connection conn = DBConnection.getConnection()) {
                Approval approval = approvalDao.findById(conn, approvalId);
                ApiResponse.success(resp, 201, toJson(approval));
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

    private static Map<String, Object> toJson(Approval approval) {
        return JsonUtil.object(
                "approvalId", approval.getApprovalId(),
                "itemId", approval.getItemId(),
                "alertId", approval.getAlertId(),
                "requestType", approval.getRequestType(),
                "requestedQty", approval.getRequestedQty(),
                "partnerId", approval.getPartnerId(),
                "status", approval.getStatus(),
                "requestedBy", approval.getRequestedBy(),
                "requestedAt", approval.getRequestedAt()
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
