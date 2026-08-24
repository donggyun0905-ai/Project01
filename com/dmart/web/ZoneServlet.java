package com.dmart.web;

import com.dmart.dao.StockLotDao;
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
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// API_명세.md 4번 참고. 단건 조회는 명세에 없어서(목록만 있음) doGet은 항상 목록으로 처리.
@WebServlet("/api/zones/*")
public class ZoneServlet extends HttpServlet {

    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();

    // doGet에 WarehouseServlet/StockLotServlet과 같은 "STAFF는 배정 창고만" 제한이 일부러 없다 -
    // STAFF가 자기 담당이 아닌 창고의 구역 상태(용량/사용량)도 볼 수 있어야 이상을 먼저 알아채고
    // 관리자에게 알릴 수 있기 때문. 쓰기(POST/PUT/DELETE)는 아래처럼 전부 requireAdmin으로 막혀
    // 있어서 조회만 넓게 열려 있어도 실제 조작 권한은 안 새어나간다. 실수로 빼먹은 게 아니니
    // 여길 "고치겠다"고 STAFF 제한을 추가하지 말 것.
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
            List<Object> data = new ArrayList<>();
            for (Zone zone : zones) {
                data.add(toJson(conn, zone));
            }
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
            ApiResponse.success(resp, 201, toJson(conn, zone));
        } catch (SQLIntegrityConstraintViolationException e) {
            // ZONE(warehouse_id, zone_name) UNIQUE 제약 위반(MySQL 에러코드 1062) - 같은 창고에
            // 같은 이름 구역을 동시에 두 번 저장하려 할 때 여기로 온다. 그 외 제약 위반(예: FK)은
            // 예상 밖의 상황이라 그냥 500으로 둔다.
            if (e.getErrorCode() == 1062) {
                ApiResponse.error(resp, 409, "CONFLICT", "이 창고에는 이미 같은 이름의 구역이 있습니다");
            } else {
                getServletContext().log("구역 등록 중 DB 오류", e);
                ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
            }
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
            ApiResponse.success(resp, 200, toJson(conn, zone));
        } catch (SQLIntegrityConstraintViolationException e) {
            // doPost와 동일 - 이름을 바꿔서 저장했는데 그 창고에 이미 같은 이름의 구역이 있는 경우.
            if (e.getErrorCode() == 1062) {
                ApiResponse.error(resp, 409, "CONFLICT", "이 창고에는 이미 같은 이름의 구역이 있습니다");
            } else {
                getServletContext().log("구역 수정 중 DB 오류", e);
                ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
            }
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

    // usedQuantity: 이 구역에 지금 들어있는 NORMAL 재고 합계 - warehouse.html이 예전엔 이걸
    // /api/stock-lots를 통째로 받아 클라이언트에서 더했는데, 200개씩 페이지네이션 되다 보니
    // 재고가 400개(2페이지)를 넘으면 뒤쪽이 조용히 빠지는 문제가 있었다. ItemServlet의
    // totalQuantity와 같은 방식으로 서버에서 정확히 계산해 내려준다.
    private Map<String, Object> toJson(Connection conn, Zone zone) throws SQLException {
        int usedQuantity = stockLotDao.sumQuantityByZoneId(conn, zone.getZoneId());
        return JsonUtil.object(
                "zoneId", zone.getZoneId(),
                "warehouseId", zone.getWarehouseId(),
                "zoneName", zone.getZoneName(),
                "capacity", zone.getCapacity(),
                "usedQuantity", usedQuantity
        );
    }
}
