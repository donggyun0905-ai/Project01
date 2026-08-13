package com.dmart.web;

import com.dmart.dao.AppUserDao;
import com.dmart.dao.UserWarehouseDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.dto.UserWarehouse;
import com.dmart.service.UserService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// API_명세.md 2.3~2.4 참고. 경로가 /{userId}, /{userId}/warehouses, /{userId}/activate 세 가지로
// 갈라지는 것 외엔 ItemServlet(3번)과 같은 CRUD 골격.
@WebServlet("/api/users/*")
public class UserServlet extends HttpServlet {

    private final AppUserDao appUserDao = new AppUserDao();
    private final UserWarehouseDao userWarehouseDao = new UserWarehouseDao();
    private final UserService userService = new UserService();

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
        String pathInfo = req.getPathInfo();
        try {
            if (pathInfo == null || "/".equals(pathInfo)) {
                doList(req, resp);
            } else {
                doGetOne(req, resp, pathInfo);
            }
        } catch (SQLException e) {
            getServletContext().log("사용자 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private void doList(HttpServletRequest req, HttpServletResponse resp) throws SQLException, IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        String role = req.getParameter("role");
        String activeParam = req.getParameter("active");
        Boolean active = activeParam != null ? Boolean.valueOf(activeParam) : Boolean.TRUE; // 기본 active=true(10.1과 동일한 관례)
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<AppUser> users = appUserDao.findPage(conn, role, active, pg.offset, pg.size);
            int total = appUserDao.count(conn, role, active);
            List<Object> data = new java.util.ArrayList<>();
            for (AppUser u : users) {
                data.add(toJson(conn, u));
            }
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        }
    }

    private void doGetOne(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws SQLException, IOException {
        Long userId = parseId(pathInfo);
        if (userId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 userId입니다");
            return;
        }
        if (!AuthUtil.isAdmin(req) && !userId.equals(AuthUtil.getUserId(req))) {
            ApiResponse.error(resp, 403, "FORBIDDEN", "본인 또는 관리자만 조회할 수 있습니다");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            AppUser user = appUserDao.findById(conn, userId);
            if (user == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 userId입니다: " + userId);
                return;
            }
            ApiResponse.success(resp, 200, toJson(conn, user));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String name = (String) body.get("name");
        String role = (String) body.get("role");
        if (username == null || password == null || name == null || role == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "username, password, name, role은 필수입니다");
            return;
        }

        try {
            Long userId = userService.create(username, password, name, role);
            try (Connection conn = DBConnection.getConnection()) {
                AppUser user = appUserDao.findById(conn, userId);
                ApiResponse.success(resp, 201, toJson(conn, user));
            }
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("사용자 등록 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        Long warehousesUserId = parseSuffixPath(pathInfo, "warehouses");
        if (warehousesUserId != null) {
            doPutWarehouses(req, resp, warehousesUserId);
            return;
        }

        Long userId = parseId(pathInfo);
        if (userId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 userId입니다");
            return;
        }
        if (!AuthUtil.isAdmin(req) && !userId.equals(AuthUtil.getUserId(req))) {
            ApiResponse.error(resp, 403, "FORBIDDEN", "본인 또는 관리자만 수정할 수 있습니다");
            return;
        }

        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }
        String name = (String) body.get("name");
        String password = (String) body.get("password");

        try {
            userService.update(userId, name, password);
            try (Connection conn = DBConnection.getConnection()) {
                AppUser user = appUserDao.findById(conn, userId);
                ApiResponse.success(resp, 200, toJson(conn, user));
            }
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("사용자 수정 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    // 2.4 참고 — PUT /api/users/{userId}/warehouses, ADMIN만
    private void doPutWarehouses(HttpServletRequest req, HttpServletResponse resp, Long userId) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }
        Object rawIds = body.get("warehouseIds");
        if (!(rawIds instanceof List)) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "warehouseIds는 배열이어야 합니다");
            return;
        }
        List<Long> warehouseIds = ((List<?>) rawIds).stream()
                .map(v -> ((Number) v).longValue())
                .collect(Collectors.toList());

        try {
            userService.replaceWarehouses(userId, warehouseIds);
            ApiResponse.success(resp, 200, JsonUtil.object("userId", userId, "warehouseIds", warehouseIds));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("창고 배정 수정 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    // 2.3 DELETE 참고 — 물리 삭제가 아니라 is_active=false로 바꾸는 소프트 삭제, ADMIN만
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        Long userId = parseId(req.getPathInfo());
        if (userId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 userId입니다");
            return;
        }
        try {
            userService.setActive(userId, false);
            ApiResponse.success(resp, 200, JsonUtil.object("userId", userId, "isActive", false));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("사용자 비활성화 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    // PATCH /api/users/{userId}/activate, ADMIN만
    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        Long userId = parseSuffixPath(req.getPathInfo(), "activate");
        if (userId == null) {
            ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다");
            return;
        }
        try {
            userService.setActive(userId, true);
            ApiResponse.success(resp, 200, JsonUtil.object("userId", userId, "isActive", true));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("사용자 재활성화 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private Map<String, Object> toJson(Connection conn, AppUser user) throws SQLException {
        List<Long> warehouseIds = userWarehouseDao.findByUserId(conn, user.getUserId()).stream()
                .map(UserWarehouse::getWarehouseId)
                .collect(Collectors.toList());
        // password는 절대 내려주지 않음 — 2.3 참고
        return JsonUtil.object(
                "userId", user.getUserId(),
                "username", user.getUsername(),
                "name", user.getName(),
                "role", user.getRole(),
                "isActive", user.getIsActive(),
                "createdAt", user.getCreatedAt(),
                "warehouseIds", warehouseIds
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

    // "/{id}/{suffix}" 형태만 허용 (예: "/8/warehouses", "/8/activate")
    private Long parseSuffixPath(String pathInfo, String suffix) {
        if (pathInfo == null) {
            return null;
        }
        String[] parts = pathInfo.split("/");
        if (parts.length != 3 || !suffix.equals(parts[2])) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
