package com.dmart.web;

import com.dmart.dao.AlertDao;
import com.dmart.dao.UserWarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.UserWarehouse;
import com.dmart.dto.Zone;
import com.dmart.util.ApiResponse;
import com.dmart.util.AuthUtil;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// API_명세.md 11번 참고. GET(목록)/PATCH(해결 처리) 둘 다 "로그인"이면 되고 ADMIN 제한은 없음.
// 다만 v3.2 — 1.4 "STAFF는 배정된 창고 관련 리소스만" 원칙을 창고정리추천에도 적용한다: 그 알림만
// 메시지에 담긴 두 구역(zoneId) 중 하나라도 배정 창고에 속해야 STAFF에게 보인다. 재고부족/재고초과/
// 이상출고는 특정 창고에 묶인 알림이 아니라(그 품목 재고가 여러 창고에 걸쳐 있을 수 있음) 그대로 둔다.
// HttpServlet엔 doPatch가 기본 제공되지 않아서 service()를 오버라이드해 PATCH만 따로 분기한다
// (이 프로젝트에서 PATCH를 쓰는 첫 엔드포인트라 여기서 패턴을 확립 — ApprovalServlet도 동일하게 따름).
@WebServlet("/api/alerts/*")
public class AlertServlet extends HttpServlet {

    private static final String CONSOLIDATION_TYPE = "창고정리추천";
    private static final Pattern ZONE_ID_PATTERN = Pattern.compile("zoneId=(\\d+)");

    private final AlertDao alertDao = new AlertDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final UserWarehouseDao userWarehouseDao = new UserWarehouseDao();

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
            List<Alert> page;
            int total;

            if (AuthUtil.isAdmin(req)) {
                // ADMIN은 거를 게 없으니 원래대로 SQL이 직접 페이지를 나눠 준다(이 목록이 커져도 가벼움).
                page = alertDao.findPage(conn, resolved, itemId, pg.offset, pg.size);
                total = alertDao.count(conn, resolved, itemId);
            } else {
                // 창고정리추천은 message 안의 zoneId를 읽어 걸러야 해서 SQL만으로는 안 되므로,
                // 조건에 맞는 전체를 받아 자바에서 거른 뒤 그 결과 기준으로 직접 페이지를 자른다
                // (LIMIT/OFFSET을 먼저 걸고 나중에 거르면 total과 실제 개수가 어긋나는 문제가 생김).
                List<Alert> matching = alertDao.findAllMatching(conn, resolved, itemId);
                List<Long> allowedWarehouseIds = userWarehouseDao.findByUserId(conn, AuthUtil.getUserId(req)).stream()
                        .map(UserWarehouse::getWarehouseId)
                        .collect(Collectors.toList());
                List<Alert> filtered = filterConsolidationByWarehouse(conn, matching, allowedWarehouseIds);
                total = filtered.size();
                page = paginate(filtered, pg.offset, pg.size);
            }

            List<Object> data = page.stream().map(AlertServlet::toJson).collect(Collectors.toList());
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("알림 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    // 창고정리추천이 아니면 그대로 두고, 창고정리추천이면 메시지 속 두 zoneId 중 하나라도
    // 배정 창고에 속해야 남긴다.
    private List<Alert> filterConsolidationByWarehouse(Connection conn, List<Alert> alerts,
                                                         List<Long> allowedWarehouseIds) throws SQLException {
        Map<Long, Long> zoneWarehouseId = new HashMap<>();
        for (Zone zone : zoneDao.findAll(conn)) {
            zoneWarehouseId.put(zone.getZoneId(), zone.getWarehouseId());
        }
        Set<Long> allowed = new HashSet<>(allowedWarehouseIds);

        List<Alert> result = new ArrayList<>();
        for (Alert alert : alerts) {
            if (!CONSOLIDATION_TYPE.equals(alert.getAlertType())) {
                result.add(alert);
                continue;
            }
            if (isAssignedZoneMentioned(alert.getMessage(), zoneWarehouseId, allowed)) {
                result.add(alert);
            }
        }
        return result;
    }

    private boolean isAssignedZoneMentioned(String message, Map<Long, Long> zoneWarehouseId, Set<Long> allowed) {
        if (message == null) {
            return false;
        }
        Matcher m = ZONE_ID_PATTERN.matcher(message);
        while (m.find()) {
            Long warehouseId = zoneWarehouseId.get(Long.valueOf(m.group(1)));
            if (warehouseId != null && allowed.contains(warehouseId)) {
                return true;
            }
        }
        return false;
    }

    private List<Alert> paginate(List<Alert> all, int offset, int size) {
        if (offset >= all.size()) {
            return new ArrayList<>();
        }
        int end = Math.min(offset + size, all.size());
        return all.subList(offset, end);
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
