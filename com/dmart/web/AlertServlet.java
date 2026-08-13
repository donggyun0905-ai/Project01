package com.dmart.web;

import com.dmart.dao.AlertDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.util.ApiResponse;
import com.dmart.util.JsonUtil;
import com.dmart.util.Pagination;

import jakarta.servlet.ServletException;
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

// API_명세.md 11번 참고. GET(목록)/PATCH(해결 처리) 둘 다 "로그인"이면 되고 ADMIN 제한은 없음.
// HttpServlet엔 doPatch가 기본 제공되지 않아서 service()를 오버라이드해 PATCH만 따로 분기한다
// (이 프로젝트에서 PATCH를 쓰는 첫 엔드포인트라 여기서 패턴을 확립 — ApprovalServlet도 동일하게 따름).
@WebServlet("/api/alerts/*")
public class AlertServlet extends HttpServlet {

    private final AlertDao alertDao = new AlertDao();

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
        String resolvedParam = req.getParameter("resolved");
        Boolean resolved = resolvedParam != null ? Boolean.valueOf(resolvedParam) : null;
        Long itemId = parseLongParam(req.getParameter("itemId"));
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<Alert> alerts = alertDao.findPage(conn, resolved, itemId, pg.offset, pg.size);
            int total = alertDao.count(conn, resolved, itemId);
            List<Object> data = alerts.stream().map(AlertServlet::toJson).collect(Collectors.toList());
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("알림 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long alertId = parseResolvePath(req.getPathInfo());
        if (alertId == null) {
            ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            Alert alert = alertDao.findById(conn, alertId);
            if (alert == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 alertId입니다: " + alertId);
                return;
            }
            alert.setIsResolved(true);
            alertDao.update(conn, alert);
            ApiResponse.success(resp, 200, toJson(alert));
        } catch (SQLException e) {
            getServletContext().log("알림 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    // "/{alertId}/resolve" 형태만 허용
    private Long parseResolvePath(String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        String[] parts = pathInfo.split("/");
        if (parts.length != 3 || !"resolve".equals(parts[2])) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
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

    private static Map<String, Object> toJson(Alert alert) {
        return JsonUtil.object(
                "alertId", alert.getAlertId(),
                "itemId", alert.getItemId(),
                "alertType", alert.getAlertType(),
                "message", alert.getMessage(),
                "isResolved", alert.getIsResolved(),
                "createdAt", alert.getCreatedAt()
        );
    }
}
