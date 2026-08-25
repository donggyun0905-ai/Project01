package com.dmart.web;

import com.dmart.service.WarehouseConsolidationService;
import com.dmart.util.ApiResponse;
import com.dmart.util.JsonUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

// WarehouseConsolidationService.scan()은 원래 서버 시작 시 1회만 도는데(WarehouseConsolidationStartupListener),
// 관리자가 알림 화면에서 "지금 바로" 다시 스캔해 보고 싶을 때 쓰는 수동 트리거. 로직 자체는 그대로 재사용 —
// scan()이 이미 같은 품목에 미해결 추천이 있으면 중복 생성 안 하므로 여러 번 눌러도 안전하고
// (AlertDao.existsUnresolvedByItemIdAndType), 그 미해결 추천이 낡아서(가리키는 구역에 재고가 이미 없음)
// 새 추천을 막고 있는 경우는 scan() 시작 시점에 자동으로 정리(해결 처리)한다(resolveStaleRecommendations).
@WebServlet("/api/warehouse-consolidation/scan")
public class WarehouseConsolidationScanServlet extends HttpServlet {

    private final WarehouseConsolidationService consolidationService = new WarehouseConsolidationService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            int createdCount = consolidationService.scan();
            ApiResponse.success(resp, 200, JsonUtil.object("createdCount", createdCount));
        } catch (SQLException e) {
            getServletContext().log("창고정리 수동 스캔 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }
}
