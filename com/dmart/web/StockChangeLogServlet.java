package com.dmart.web;

import com.dmart.dao.StockChangeLogDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.StockChangeLog;
import com.dmart.dto.StockLot;
import com.dmart.service.StockLotAdjustmentService;
import com.dmart.util.ApiResponse;
import com.dmart.util.AuthUtil;
import com.dmart.util.JsonUtil;
import com.dmart.util.Pagination;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// API_명세.md 10.4(복원) + 13번(감사 로그 조회) 참고. 둘 다 ADMIN 전용, 같은 URL 패턴이라 한 서블릿에서 처리.
@WebServlet("/api/stock-change-logs/*")
public class StockChangeLogServlet extends HttpServlet {

    private final StockLotAdjustmentService adjustmentService = new StockLotAdjustmentService();
    private final StockChangeLogDao stockChangeLogDao = new StockChangeLogDao();
    private final StockLotDao stockLotDao = new StockLotDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long lotId = parseLongParam(req.getParameter("lotId"));
        String changeType = req.getParameter("changeType");
        LocalDate from = parseDateParam(req.getParameter("from"));
        LocalDate to = parseDateParam(req.getParameter("to"));
        String keyword = blankToNull(req.getParameter("keyword"));
        String changedByKeyword = blankToNull(req.getParameter("changedByKeyword"));
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<StockChangeLog> logs = stockChangeLogDao.findPage(conn, lotId, changeType, from, to, keyword, changedByKeyword, pg.offset, pg.size);
            int total = stockChangeLogDao.count(conn, lotId, changeType, from, to, keyword, changedByKeyword);
            List<Object> data = new ArrayList<>();
            for (StockChangeLog log : logs) {
                // 화면에서 품목명을 보여주려면 itemId가 필요한데 STOCK_CHANGE_LOG 자체엔
                // 없어서 로트를 한 번 더 찾아 붙여준다 (ItemServlet의 totalQuantity와 같은 방식).
                StockLot lot = stockLotDao.findById(conn, log.getLotId());
                data.add(toJson(log, lot != null ? lot.getItemId() : null));
            }
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("감사 로그 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long logId = parseRestorePath(req.getPathInfo());
        if (logId == null) {
            ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다");
            return;
        }

        try {
            StockLotAdjustmentService.RestoreResult result =
                    adjustmentService.restore(logId, AuthUtil.getUserId(req));
            EventBus.publish("auditLog");
            ApiResponse.success(resp, 200, JsonUtil.object(
                    "lotId", result.lotId,
                    "restoredQuantity", result.restoredQuantity,
                    "restoredStatus", result.restoredStatus
            ));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("복원 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    // "/{logId}/restore" 형태만 허용
    private Long parseRestorePath(String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        String[] parts = pathInfo.split("/");
        if (parts.length != 3 || !"restore".equals(parts[2])) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
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

    private LocalDate parseDateParam(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Map<String, Object> toJson(StockChangeLog log, Long itemId) {
        return JsonUtil.object(
                "logId", log.getLogId(),
                "lotId", log.getLotId(),
                "itemId", itemId,
                "changedBy", log.getChangedBy(),
                "changeType", log.getChangeType(),
                "beforeValue", log.getBeforeValue(),
                "afterValue", log.getAfterValue(),
                "reason", log.getReason(),
                "isReverted", log.getIsReverted(),
                "changedAt", log.getChangedAt()
        );
    }
}
