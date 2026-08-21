package com.dmart.web;

import com.dmart.dao.ReturnDisposalDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.ReturnDisposal;
import com.dmart.dto.StockLot;
import com.dmart.service.ReturnDisposalService;
import com.dmart.util.ApiResponse;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// API_명세.md 9번 참고. ReturnDisposalService를 감싸는 얇은 서블릿.
// processedBy도 8번의 handlerId와 마찬가지로 요청 바디에서 받는다 (9번 요청 예시에 명시됨).
// GET(반품/폐기 이력 목록)은 나중에 return.html에서 이력을 보여주려고 추가함.
@WebServlet("/api/returns")
public class ReturnDisposalServlet extends HttpServlet {

    private final ReturnDisposalService returnDisposalService = new ReturnDisposalService();
    private final ReturnDisposalDao returnDisposalDao = new ReturnDisposalDao();
    private final StockLotDao stockLotDao = new StockLotDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long itemId = parseLongParam(req.getParameter("itemId"));
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<ReturnDisposal> list = returnDisposalDao.findPage(conn, itemId, pg.offset, pg.size);
            int total = returnDisposalDao.count(conn, itemId);
            List<Object> data = new ArrayList<>();
            for (ReturnDisposal record : list) {
                // 화면에서 품목명/구역을 보여주려면 itemId/zoneId가 필요한데 RETURN_DISPOSAL
                // 자체엔 없어서 로트를 한 번 더 찾아 붙여준다 (ItemServlet의 totalQuantity와 같은 방식).
                StockLot lot = stockLotDao.findById(conn, record.getLotId());
                data.add(toJson(record, lot != null ? lot.getItemId() : null, lot != null ? lot.getZoneId() : null));
            }
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("반품/폐기 이력 조회 중 DB 오류", e);
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

        Long lotId = RequestUtil.toLong(body.get("lotId"));
        String type = (String) body.get("type");
        String reason = (String) body.get("reason");
        Integer quantity = RequestUtil.toInteger(body.get("quantity"));
        Long processedBy = RequestUtil.toLong(body.get("processedBy"));
        String processedDateStr = (String) body.get("processedDate");
        if (lotId == null || type == null || quantity == null || processedBy == null || processedDateStr == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR",
                    "lotId, type, quantity, processedBy, processedDate는 필수입니다");
            return;
        }

        LocalDate processedDate;
        try {
            processedDate = LocalDate.parse(processedDateStr);
        } catch (DateTimeParseException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "processedDate 형식이 올바르지 않습니다 (yyyy-MM-dd)");
            return;
        }

        try {
            ReturnDisposalService.ReturnDisposalResult result =
                    returnDisposalService.process(lotId, type, reason, quantity, processedBy, processedDate);
            ApiResponse.success(resp, 201, JsonUtil.object(
                    "recordId", result.recordId,
                    "splitOccurred", result.splitOccurred,
                    "disposedLotId", result.disposedLotId,
                    "alertCreated", result.alertCreated,
                    "approvalId", result.approvalId
            ));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("반품/폐기 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private static Map<String, Object> toJson(ReturnDisposal record, Long itemId, Long zoneId) {
        return JsonUtil.object(
                "recordId", record.getRecordId(),
                "lotId", record.getLotId(),
                "itemId", itemId,
                "zoneId", zoneId,
                "type", record.getType(),
                "reason", record.getReason(),
                "quantity", record.getQuantity(),
                "processedBy", record.getProcessedBy(),
                "processedDate", record.getProcessedDate()
        );
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
}
