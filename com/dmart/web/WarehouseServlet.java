package com.dmart.web;

import com.dmart.dao.UserWarehouseDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.UserWarehouse;
import com.dmart.dto.Warehouse;
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

// API_명세.md 4번 참고. ITEM(3번)과 같은 CRUD 패턴 + STAFF는 배정 창고만 조회 가능한 제약 추가.
@WebServlet("/api/warehouses/*")
public class WarehouseServlet extends HttpServlet {

    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final UserWarehouseDao userWarehouseDao = new UserWarehouseDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        try {
            if (pathInfo == null || "/".equals(pathInfo)) {
                doList(req, resp);
            } else {
                doGetOne(req, resp, pathInfo);
            }
        } catch (SQLException e) {
            getServletContext().log("창고 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private void doList(HttpServletRequest req, HttpServletResponse resp) throws SQLException, IOException {
        Pagination pg = Pagination.from(req);
        // STAFF는 배정 창고만 — 4번 참고. ADMIN은 제한 없음(allowedIds=null).
        List<Long> allowedIds = null;
        try (Connection conn = DBConnection.getConnection()) {
            if (!AuthUtil.isAdmin(req)) {
                allowedIds = userWarehouseDao.findByUserId(conn, AuthUtil.getUserId(req)).stream()
                        .map(UserWarehouse::getWarehouseId)
                        .collect(Collectors.toList());
            }
            List<Warehouse> warehouses = warehouseDao.findPage(conn, allowedIds, pg.offset, pg.size);
            int total = warehouseDao.count(conn, allowedIds);
            List<Object> data = warehouses.stream().map(WarehouseServlet::toJson).collect(Collectors.toList());
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        }
    }

    private void doGetOne(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws SQLException, IOException {
        Long warehouseId = parseId(pathInfo);
        if (warehouseId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 warehouseId입니다");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Warehouse warehouse = warehouseDao.findById(conn, warehouseId);
            if (warehouse == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 warehouseId입니다: " + warehouseId);
                return;
            }
            if (!AuthUtil.isAdmin(req)) {
                UserWarehouse assigned = userWarehouseDao.findById(conn, AuthUtil.getUserId(req), warehouseId);
                if (assigned == null) {
                    ApiResponse.error(resp, 403, "FORBIDDEN", "배정되지 않은 창고입니다");
                    return;
                }
            }
            ApiResponse.success(resp, 200, toJson(warehouse));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        Warehouse warehouse = parseBody(req, resp);
        if (warehouse == null) {
            return;
        }
        if (warehouse.getName() == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "name은 필수입니다");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Long warehouseId = warehouseDao.insert(conn, warehouse);
            warehouse.setWarehouseId(warehouseId);
            ApiResponse.success(resp, 201, toJson(warehouse));
        } catch (SQLException e) {
            getServletContext().log("창고 등록 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        Long warehouseId = parseId(req.getPathInfo());
        if (warehouseId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 warehouseId입니다");
            return;
        }
        Warehouse warehouse = parseBody(req, resp);
        if (warehouse == null) {
            return;
        }
        warehouse.setWarehouseId(warehouseId);
        try (Connection conn = DBConnection.getConnection()) {
            if (warehouseDao.findById(conn, warehouseId) == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 warehouseId입니다: " + warehouseId);
                return;
            }
            warehouseDao.update(conn, warehouse);
            ApiResponse.success(resp, 200, toJson(warehouse));
        } catch (SQLException e) {
            getServletContext().log("창고 수정 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        Long warehouseId = parseId(req.getPathInfo());
        if (warehouseId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 warehouseId입니다");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            boolean deleted = warehouseDao.deleteById(conn, warehouseId);
            if (!deleted) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 warehouseId입니다: " + warehouseId);
                return;
            }
            ApiResponse.success(resp, 200, JsonUtil.object("warehouseId", warehouseId));
        } catch (SQLException e) {
            // ZONE 등이 이 창고를 참조 중이면 FK 위반으로 삭제가 막힘
            ApiResponse.error(resp, 409, "CONFLICT", "다른 데이터에서 참조 중이라 삭제할 수 없습니다");
        }
    }

    private Warehouse parseBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return null;
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setName((String) body.get("name"));
        warehouse.setLocation((String) body.get("location"));
        return warehouse;
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

    private static Map<String, Object> toJson(Warehouse warehouse) {
        return JsonUtil.object(
                "warehouseId", warehouse.getWarehouseId(),
                "name", warehouse.getName(),
                "location", warehouse.getLocation()
        );
    }
}
