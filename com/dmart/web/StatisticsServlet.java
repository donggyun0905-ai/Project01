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
