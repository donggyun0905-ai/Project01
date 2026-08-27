package com.dmart.web;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.dmart.report.StatisticsService;
import com.dmart.report.dto.ClientMonthlyTrend;
import com.dmart.report.dto.ClientOutboundRanking;
import com.dmart.report.dto.PeriodInOutStat;
import com.dmart.report.dto.StockTurnover;
import com.dmart.util.ApiResponse;
import com.dmart.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * 통계 대시보드 조회 API
 *
 * 기본 URL
 * /api/statistics/*
 *
 * 지원 경로
 * - /inout
 * - /turnover
 * - /stockout-forecast
 * - /overstock-forecast
 * - /clients/top5
 * - /clients/monthly
 * - /warehouseStock
 */
@WebServlet("/api/statistics/*")
public class StatisticsServlet extends HttpServlet {
	private final StatisticsService service = new StatisticsService();
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String path = req.getPathInfo();
		
		try {
			if(path == null) {
				ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다.");
				return;
			}
			
			switch (path) {
			case "/inout":
				getInOutStatistics(req, resp);
				break;
			case "/turnover":
				getStockTurnover(resp);
				break;
			case "/stockout-forecast":
				getStockoutForecast(req, resp);
				break;
			case "/overstock-forecast":
				getOverstockForecast(req, resp);
				break;
			case "/clients/top5":
				getOutboundRank(req, resp);
				break;
			case "/clients/monthly":
				getMonthlyTrend(req, resp);
				break;
			case "/warehouseStock":
				getWarehouseStock(resp);
				break;
			default:
				ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다.");
			}
		} catch (DateTimeParseException e) {
			ApiResponse.error(resp, 400, "VALIDATION_ERROR", "날짜 형식이 올바르지 않습니다.");
		} catch (IllegalArgumentException e) {
			ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
		} catch (Exception e) {
			getServletContext().log("통계 조회 중 오류", e);
			ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
		}
	}
	
	// 기간별 입출고량 통계 조회
	private void getInOutStatistics(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
		String unit = req.getParameter("unit");
		String fromParam = req.getParameter("from");
		String toParam = req.getParameter("to");
		String warehouseName = req.getParameter("warehouse");
		
		LocalDate from = parseDate(fromParam);
		LocalDate to = parseDate(toParam);
		List<PeriodInOutStat> list = service.getInOutStatistics(unit, from, to, warehouseName);
		List<Object> data = list.stream().map(StatisticsServlet::inOutToJson).collect(Collectors.toList());
		
		ApiResponse.success(resp, 200, data);
	}
	
	// 재고 회전율 조회
	private void getStockTurnover (HttpServletResponse resp) throws ServletException, IOException{
		List<StockTurnover> list = service.getTop6StockTurnover();
		List<Object> data = list.stream().map(StatisticsServlet::turnoverToJson).collect(Collectors.toList());
		ApiResponse.success(resp, 200, data);
	}

	// 재고 소진 예상 조회 - 통계 페이지 입출고량 집계 카드 우측 패널용
	private void getStockoutForecast(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		int limit = 5;
		String limitParam = req.getParameter("limit");
		if (limitParam != null) {
			try {
				limit = Integer.parseInt(limitParam);
			} catch (NumberFormatException ignored) {
				// 잘못된 값이면 기본값(5)을 그대로 씀
			}
		}
		List<StockTurnover> list = service.getStockoutForecast(limit);
		List<Object> data = list.stream().map(StatisticsServlet::stockoutToJson).collect(Collectors.toList());
		ApiResponse.success(resp, 200, data);
	}

	// 재고초과 임박 예상 조회 - 통계 페이지 입출고량 집계 카드 우측 패널용(재고 소진 예상과 짝)
	private void getOverstockForecast(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		int limit = 5;
		String limitParam = req.getParameter("limit");
		if (limitParam != null) {
			try {
				limit = Integer.parseInt(limitParam);
			} catch (NumberFormatException ignored) {
				// 잘못된 값이면 기본값(5)을 그대로 씀
			}
		}
		List<StockTurnover> list = service.getOverstockForecast(limit);
		List<Object> data = list.stream().map(StatisticsServlet::overstockToJson).collect(Collectors.toList());
		ApiResponse.success(resp, 200, data);
	}
	
	// 거래처별 출고량 TOP5 조회
	private void getOutboundRank (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
		LocalDate from = parseDate(req.getParameter("from"));
		LocalDate to = parseDate(req.getParameter("to"));
		List<ClientOutboundRanking> list = service.getTop5Outbound(from, to);
		List<Object> data = list.stream().map(StatisticsServlet::outboundRankToJson).collect(Collectors.toList());
		ApiResponse.success(resp, 200, data);
	}
	
	// 거래처별 월별 출고 추이 조회
	private void getMonthlyTrend (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
		String start = req.getParameter("start");
		String end = req.getParameter("end");
		List<ClientMonthlyTrend> list = service.getMonthlyTrend(start, end);
		List<Object> data = list.stream().map(StatisticsServlet::monthlyTrendToJson).collect(Collectors.toList());
		ApiResponse.success(resp, 200, data);
	}
	
	// 창고별 현재 재고량 조회
	private void getWarehouseStock(HttpServletResponse resp) throws ServletException, IOException{
		Map<String, Integer> stock = service.getWarehouseStock();
		
		int totalQty = stock.values().stream().mapToInt(Integer::intValue).sum();
		
		List<Object> warehouses = stock.entrySet().stream()
				.map(entry -> (Object) JsonUtil.object("warehouseName", entry.getKey(), "stockQty", entry.getValue()))
				.collect(Collectors.toList());
		
		Map<String, Object> data = JsonUtil.object("totalQty", totalQty, "warehouses", warehouses);
		
		ApiResponse.success(resp, 200, data);
	}
	
	private static LocalDate parseDate(String value) {
		if(value == null || value.isBlank()) return null; 
		
		return LocalDate.parse(value);
	}
	
	// 기간별 입출고 통계 JSON 구조 변환
	private static Map<String, Object> inOutToJson(PeriodInOutStat stat){
		return JsonUtil.object(
				"period", stat.getPeriod(),
				"inboundQty", stat.getInboundQty(),
				"outboundQty", stat.getOutboundQty()
		);
	}
	
	// 재고 회전율 JSON 구조 변환
	private static Map<String, Object> turnoverToJson(StockTurnover stock){
		return JsonUtil.object(
				"itemId", stock.getItemId(),
				"itemName", stock.getItemName(),
				"inboundDate", stock.getInboundDate(),
				"currentStockQty", stock.getCurrentStockQty(),
				"dailyVelocity", stock.getDailyVelocity(),
				"turnoverRatio", stock.getTurnoverRatio(),
				"status", stock.getStatus()
		);
	}

	// 재고 소진 예상 JSON 구조 변환 - daysLeft = 현재재고 ÷ 일평균 소진 속도(소수점 첫째 자리까지)
	private static Map<String, Object> stockoutToJson(StockTurnover stock) {
		double daysLeft = stock.getCurrentStockQty() / stock.getDailyVelocity().doubleValue();
		return JsonUtil.object(
				"itemId", stock.getItemId(),
				"itemName", stock.getItemName(),
				"currentStockQty", stock.getCurrentStockQty(),
				"dailyVelocity", stock.getDailyVelocity(),
				"daysLeft", Math.round(daysLeft * 10) / 10.0
		);
	}

	// 재고초과 임박 예상 JSON 구조 변환 - daysUntilFull = (기준-현재재고) ÷ 일평균 입고 속도
	private static Map<String, Object> overstockToJson(StockTurnover stock) {
		double daysUntilFull = (stock.getCapacityMax() - stock.getCurrentStockQty())
				/ stock.getInboundDailyVelocity().doubleValue();
		return JsonUtil.object(
				"itemId", stock.getItemId(),
				"itemName", stock.getItemName(),
				"currentStockQty", stock.getCurrentStockQty(),
				"capacityMax", stock.getCapacityMax(),
				"inboundDailyVelocity", stock.getInboundDailyVelocity(),
				"daysUntilFull", Math.round(daysUntilFull * 10) / 10.0
		);
	}
	
	// 거래처별 출고량 TOP5 JSON 구조 변환
	private static Map<String, Object> outboundRankToJson(ClientOutboundRanking rank){
		return JsonUtil.object(
				"rank", rank.getRank(),
				"partnerId", rank.getPartnerId(),
				"partnerName", rank.getPartnerName(),
				"totalQty", rank.getTotalQty()
		);
	}
	
	// 거래처별 월별 출고 추이 JSON 구조 변환
	private static Map<String, Object> monthlyTrendToJson(ClientMonthlyTrend trend){
		return JsonUtil.object(
				"partnerId", trend.getPartnerId(),
				"partnerName", trend.getPartnerName(),
				"yearMonth", trend.getYearMonth(),
				"monthlyQty", trend.getMonthlyQty(),
				"transactionCount", trend.getTransactionCount()
		);
	}
}
