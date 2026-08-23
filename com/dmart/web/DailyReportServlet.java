package com.dmart.web;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.stream.Collectors;

import com.dmart.report.DailyReportService;
import com.dmart.report.dto.DailyComparison;
import com.dmart.report.dto.DailyReport;
import com.dmart.report.dto.LowStockItem;
import com.dmart.report.dto.TopOutboundItem;
import com.dmart.util.ApiResponse;
import com.dmart.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/reports/daily")
public class DailyReportServlet extends HttpServlet{
	private final DailyReportService service = new DailyReportService();
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			String dateParam = req.getParameter("date");
			LocalDate date = null;
			if(dateParam != null && !dateParam.isBlank()) date = LocalDate.parse(dateParam);
			
			DailyReport report = service.getDailyReport(date);
			Map<String, Object> data = toJson(report);
			ApiResponse.success(resp, 200, data);
			
		} catch (DateTimeParseException e) {
			ApiResponse.error(resp, 400, "VALIDATION_ERROR", "date는 yyyy-MM-dd 형식이어야 합니다.");
		} catch (IllegalArgumentException e) {
			ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
		} catch (Exception e) {
			getServletContext().log("일일보고서 조회 중 오류", e);
			ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
		}
	}
	
	private static Map<String, Object> toJson(DailyReport report) {
		return JsonUtil.object(
				"date", report.getDate(),
				"dailyComparison", comparisonToJson(report.getDailyComp()),
				"lowStockItem", report.getLowStockItem().stream().map(DailyReportServlet::lowStockItemToJson).collect(Collectors.toList()),
				"topOutboundItem", report.getTopOutItem().stream().map(DailyReportServlet::top5OutItemToJson).collect(Collectors.toList())
		);
	}
	
	private static Map<String, Object> comparisonToJson(DailyComparison dailyComp) {
		if(dailyComp == null) return null;
		
		return JsonUtil.object(
				"todayInboundQty", dailyComp.getTodayInboundQty(),
				"yesterdayInboundQty", dailyComp.getYesterdayInboundQty(),
				"todayOutboundQty", dailyComp.getTodayOutboundQty(),
				"yesterdayOutboundQty", dailyComp.getYesterdayOutboundQty(),
				"inboundQtyChange", dailyComp.getInboundQtyChange(),
				"outboundQtyChange", dailyComp.getOutboundQtyChange(),
				"inboundQtyChangeRate", dailyComp.getInboundQtyChangeRate(),
				"outboundQtyChangeRate", dailyComp.getOutboundQtyChangeRate()
		);
	}
	
	private static Map<String, Object> lowStockItemToJson(LowStockItem lowItem){
		return JsonUtil.object(
				"itemId", lowItem.getItemId(),
				"itemName", lowItem.getItemName(),
				"currentQty", lowItem.getCurrentQty(),
				"minQty", lowItem.getMinQty()
		);
	}
	
	private static Map<String, Object> top5OutItemToJson(TopOutboundItem topItem){
		return JsonUtil.object(
				"rank", topItem.getRank(),
				"itemId", topItem.getItemId(),
				"itemName", topItem.getItemName(),
				"totalOutboundQty", topItem.getTotalOutboundQty()
		);
	}
}
