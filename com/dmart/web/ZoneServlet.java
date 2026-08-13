package com.dmart.web;

import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Zone;
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

// API_명세.md 4번 참고. 단건 조회는 명세에 없어서(목록만 있음) doGet은 항상 목록으로 처리.
@WebServlet("/api/zones/*")
public class ZoneServlet extends HttpServlet {

    private final ZoneDao zoneDao = new ZoneDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long warehouseId = null;
        String warehouseIdParam = req.getParameter("warehouseId");
        if (warehouseIdParam != null) {
            try {
                warehouseId = Long.parseLong(warehouseIdParam);
            } catch (NumberFormatException e) {
                ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 warehouseId입니다");
                return;
            }
        }
        Pagination pg = Pagination.from(req);
        try (Connection conn = DBConnection.getConnection()) {
            List<Zone> zones = zoneDao.findPage(conn, warehouseId, pg.offset, pg.size);
            int total = zoneDao.count(conn, warehouseId);
            List<Object> data = zones.stream().map(ZoneServlet::toJson).collect(Collectors.toList());
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("구역 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        Zone zone = parseBody(req, resp);
        if (zone == null) {
            return;
        }
        if (zone.getWarehouseId() == null || zone.getZoneName() == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "warehouseId와 zoneName은 필수입니다");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Long zoneId = zoneDao.insert(conn, zone);
            zone.setZoneId(zoneId);
            ApiResponse.success(resp, 201, toJson(zone));
        } catch (SQLException e) {
            getServletContext().log("구역 등록 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        Long zoneId = parseId(req.getPathInfo());
        if (zoneId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 zoneId입니다");
            return;
        }
        Zone zone = parseBody(req, resp);
        if (zone == null) {
            return;
        }
        zone.setZoneId(zoneId);
        try (Connection conn = DBConnection.getConnection()) {
            if (zoneDao.findById(conn, zoneId) == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 zoneId입니다: " + zoneId);
                return;
            }
            zoneDao.update(conn, zone);
            ApiResponse.success(resp, 200, toJson(zone));
        } catch (SQLException e) {
            getServletContext().log("구역 수정 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        Long zoneId = parseId(req.getPathInfo());
        if (zoneId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 zoneId입니다");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            boolean deleted = zoneDao.deleteById(conn, zoneId);
            if (!deleted) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 zoneId입니다: " + zoneId);
                return;
            }
            ApiResponse.success(resp, 200, JsonUtil.object("zoneId", zoneId));
        } catch (SQLException e) {
            // STOCK_LOT 등이 이 구역을 참조 중이면 FK 위반으로 삭제가 막힘
            ApiResponse.error(resp, 409, "CONFLICT", "다른 데이터에서 참조 중이라 삭제할 수 없습니다");
        }
    }

    private Zone parseBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return null;
        }
        Zone zone = new Zone();
        zone.setWarehouseId(RequestUtil.toLong(body.get("warehouseId")));
        zone.setZoneName((String) body.get("zoneName"));
        zone.setCapacity(RequestUtil.toInteger(body.get("capacity")));
        return zone;
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

    private static Map<String, Object> toJson(Zone zone) {
        return JsonUtil.object(
                "zoneId", zone.getZoneId(),
                "warehouseId", zone.getWarehouseId(),
                "zoneName", zone.getZoneName(),
                "capacity", zone.getCapacity()
        );
    }
}
