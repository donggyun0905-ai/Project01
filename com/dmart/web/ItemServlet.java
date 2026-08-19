package com.dmart.web;

import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// API_명세.md 3번 참고. 다른 리소스 서블릿들이 따라갈 CRUD 패턴의 기준.
@WebServlet("/api/items/*")
public class ItemServlet extends HttpServlet {

    private final ItemDao itemDao = new ItemDao();
    private final StockLotDao stockLotDao = new StockLotDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo(); // null 또는 "/" = 목록(/api/items), "/3" = 단건(/api/items/3)
        try {
            if (pathInfo == null || "/".equals(pathInfo)) {
                doList(req, resp);
            } else {
                doGetOne(resp, pathInfo);
            }
        } catch (SQLException e) {
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private void doList(HttpServletRequest req, HttpServletResponse resp) throws SQLException, IOException {
        String category = req.getParameter("category");
        String keyword = req.getParameter("keyword");
        String activeParam = req.getParameter("active");
        Boolean active = activeParam != null ? Boolean.valueOf(activeParam) : Boolean.TRUE; // 기본 active=true(2.3과 동일한 관례)
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<Item> items = itemDao.findPage(conn, category, keyword, active, pg.offset, pg.size);
            int total = itemDao.count(conn, category, keyword, active);
            List<Object> data = new ArrayList<>();
            for (Item item : items) {
                data.add(toJson(conn, item));
            }
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        }
    }

    private void doGetOne(HttpServletResponse resp, String pathInfo) throws SQLException, IOException {
        Long itemId = parseId(pathInfo);
        if (itemId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 itemId입니다");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Item item = itemDao.findById(conn, itemId);
            if (item == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 itemId입니다: " + itemId);
                return;
            }
            ApiResponse.success(resp, 200, toJson(conn, item));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Item item = parseBody(req, resp);
        if (item == null) {
            return;
        }
        if (item.getItemName() == null || item.getUnit() == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "itemName과 unit은 필수입니다");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            Long itemId = itemDao.insert(conn, item);
            item.setItemId(itemId);
            ApiResponse.success(resp, 201, toJson(conn, item));
        } catch (SQLException e) {
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long itemId = parseId(req.getPathInfo());
        if (itemId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 itemId입니다");
            return;
        }

        Item item = parseBody(req, resp);
        if (item == null) {
            return;
        }
        item.setItemId(itemId);

        try (Connection conn = DBConnection.getConnection()) {
            if (itemDao.findById(conn, itemId) == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 itemId입니다: " + itemId);
                return;
            }
            itemDao.update(conn, item);
            ApiResponse.success(resp, 200, toJson(conn, item));
        } catch (SQLException e) {
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long itemId = parseId(req.getPathInfo());
        if (itemId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 itemId입니다");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            boolean deleted = itemDao.deleteById(conn, itemId);
            if (!deleted) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 itemId입니다: " + itemId);
                return;
            }
            ApiResponse.success(resp, 200, JsonUtil.object("itemId", itemId));
        } catch (SQLException e) {
            // STOCK_LOT 등이 이 품목을 참조 중이면 FK 위반으로 삭제가 막힘
            ApiResponse.error(resp, 409, "CONFLICT", "다른 데이터에서 참조 중이라 삭제할 수 없습니다");
        }
    }

    private Item parseBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return null;
        }
        Item item = new Item();
        item.setItemName((String) body.get("itemName"));
        item.setCategory((String) body.get("category"));
        item.setUnit((String) body.get("unit"));
        item.setThresholdMin(RequestUtil.toInteger(body.get("thresholdMin")));
        item.setCapacityMax(RequestUtil.toInteger(body.get("capacityMax")));
        item.setShelfLifeDays(RequestUtil.toInteger(body.get("shelfLifeDays")));
        item.setIsActive((Boolean) body.get("isActive"));
        return item;
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

    // totalQuantity: 이 품목이 여러 존/로트에 나뉘어 있어도 한 번에 볼 수 있는 전체(NORMAL) 재고 합계.
    private Map<String, Object> toJson(Connection conn, Item item) throws SQLException {
        int totalQuantity = stockLotDao.sumQuantityByItemId(conn, item.getItemId());
        return JsonUtil.object(
                "itemId", item.getItemId(),
                "itemName", item.getItemName(),
                "category", item.getCategory(),
                "unit", item.getUnit(),
                "thresholdMin", item.getThresholdMin(),
                "capacityMax", item.getCapacityMax(),
                "shelfLifeDays", item.getShelfLifeDays(),
                "isActive", item.getIsActive(),
                "totalQuantity", totalQuantity
        );
    }
}
