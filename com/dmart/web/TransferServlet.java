package com.dmart.web;

import com.dmart.dao.StockLotDao;
import com.dmart.dao.StockTransferDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.StockLot;
import com.dmart.dto.StockTransfer;
import com.dmart.service.TransferService;
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

// API_명세.md 8번 참고. TransferService를 감싸는 얇은 서블릿 — InboundServlet과 동일한 구조.
// handlerId는 6/7번의 createdBy와 달리 세션이 아니라 요청 바디에서 받는다 (8번 요청 예시에 명시됨).
// GET(이동 이력 목록)은 나중에 movement.html에서 이력을 보여주려고 추가함 - AlertServlet과 같이
// 로그인만 하면 되고 ADMIN 제한은 없음. from/to는 movement.html의 기간 필터용 -
// 예전엔 화면이 현재 페이지 안에서만 날짜로 걸러서 페이지네이션과 안 맞았는데, 여기서
// 걸러주면 total도 필터링된 기준으로 정확히 나온다.
@WebServlet("/api/transfers")
public class TransferServlet extends HttpServlet {

    private final TransferService transferService = new TransferService();
    private final StockTransferDao stockTransferDao = new StockTransferDao();
    private final StockLotDao stockLotDao = new StockLotDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long itemId = parseLongParam(req.getParameter("itemId"));
        LocalDate from = parseDateParam(req.getParameter("from"));
        LocalDate to = parseDateParam(req.getParameter("to"));
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<StockTransfer> list = stockTransferDao.findPage(conn, itemId, from, to, pg.offset, pg.size);
            int total = stockTransferDao.count(conn, itemId, from, to);
            List<Object> data = new ArrayList<>();
            for (StockTransfer transfer : list) {
                // 화면에서 품목명을 보여주려면 itemId가 필요한데 STOCK_TRANSFER 자체엔 없어서
                // 로트를 한 번 더 찾아 붙여준다 (ItemServlet의 totalQuantity와 같은 방식).
                StockLot lot = stockLotDao.findById(conn, transfer.getLotId());
                data.add(toJson(transfer, lot != null ? lot.getItemId() : null));
            }
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        } catch (SQLException e) {
            getServletContext().log("이동 이력 조회 중 DB 오류", e);
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
        Long fromZoneId = RequestUtil.toLong(body.get("fromZoneId"));
        Long toZoneId = RequestUtil.toLong(body.get("toZoneId"));
        Integer quantity = RequestUtil.toInteger(body.get("quantity"));
        Long handlerId = RequestUtil.toLong(body.get("handlerId"));
        if (lotId == null || fromZoneId == null || toZoneId == null || quantity == null || handlerId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR",
                    "lotId, fromZoneId, toZoneId, quantity, handlerId는 필수입니다");
            return;
        }

        try {
            TransferService.TransferResult result =
                    transferService.transfer(lotId, fromZoneId, toZoneId, quantity, handlerId);
            EventBus.publish("transfer");
            ApiResponse.success(resp, 201, JsonUtil.object(
                    "transferId", result.transferId,
                    "splitOccurred", result.splitOccurred,
                    "newLotId", result.newLotId
            ));
        } catch (IllegalArgumentException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponse.error(resp, 409, "CONFLICT", e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("재고이동 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private static Map<String, Object> toJson(StockTransfer transfer, Long itemId) {
        return JsonUtil.object(
                "transferId", transfer.getTransferId(),
                "lotId", transfer.getLotId(),
                "itemId", itemId,
                "fromZoneId", transfer.getFromZoneId(),
                "toZoneId", transfer.getToZoneId(),
                "quantity", transfer.getQuantity(),
                "handlerId", transfer.getHandlerId(),
                "movedAt", transfer.getMovedAt()
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
}
