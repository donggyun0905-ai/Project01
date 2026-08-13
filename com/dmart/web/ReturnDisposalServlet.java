package com.dmart.web;

import com.dmart.service.ReturnDisposalService;
import com.dmart.util.ApiResponse;
import com.dmart.util.JsonUtil;
import com.dmart.util.RequestUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

// API_명세.md 9번 참고. ReturnDisposalService를 감싸는 얇은 서블릿.
// processedBy도 8번의 handlerId와 마찬가지로 요청 바디에서 받는다 (9번 요청 예시에 명시됨).
@WebServlet("/api/returns")
public class ReturnDisposalServlet extends HttpServlet {

    private final ReturnDisposalService returnDisposalService = new ReturnDisposalService();

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
}
