package com.dmart.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.dmart.report.DailyReportService;
import com.dmart.report.ExportService;
import com.dmart.report.FileExportService;
import com.dmart.report.StatisticsService;
import com.dmart.report.dto.AlertHistory;
import com.dmart.report.dto.ClientMonthlyTrend;
import com.dmart.report.dto.ClientOutboundRanking;
import com.dmart.report.dto.DailyReport;
import com.dmart.report.dto.InOutLog;
import com.dmart.report.dto.PeriodInOutStat;
import com.dmart.report.dto.StatisticsReport;
import com.dmart.report.dto.StockTurnover;
import com.dmart.util.ApiResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/export/*")
public class FileExportServlet extends HttpServlet {
	private final ExportService exportService = new ExportService();
	private final StatisticsService statisticsService = new StatisticsService();
	private final DailyReportService dailyReportService = new DailyReportService();
	private final FileExportService fileExportService = new FileExportService();
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String path = req.getPathInfo();
		
		try {
			if(path == null) {
				ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다.");
				return;
			}
			
			switch (path) {
			case "/inout/csv":
				exportInOutCsv(req, resp);
				break;
			case "/alerts/csv":
				exportAlertCsv(req, resp);
				break;
			case "/statistics/csv":
				exportStatisticsCsv(req, resp);
				break;
			case "/inout/excel":
				exportInOutExcel(req, resp);
				break;
			case "/alerts/excel":
				exportAlertExcel(req, resp);
				break;
			case "/statistics/excel":
				exportStatisticsExcel(req, resp);
				break;
			case "/daily/pdf":
				exportDailyPdf(req, resp);
				break;
			default:
				ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다.");
			}
		} catch (DateTimeParseException e) {
			ApiResponse.error(resp, 400, "VALIDATION_ERROR", "날짜 형식이 올바르지 않습니다.");
		} catch (IllegalArgumentException e) {
			ApiResponse.error(resp, 400, "VALIDATION_ERROR", e.getMessage());
		} catch (Exception e) {
			getServletContext().log("파일 내보내기 중 오류", e);
			ApiResponse.error(resp, 500, "INTERNAL_ERROR", "파일 생성 중 오류가 발생했습니다.");
		}
	}
	
	private void exportInOutCsv(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		LocalDate from = parseDate(req.getParameter("from"));
		LocalDate to = parseDate(req.getParameter("to"));
		
		List<InOutLog> data = exportService.getInOutboundLog(from, to);
		
		File file = File.createTempFile("inout_log_", ".csv");
		fileExportService.exportInOutLogCsv(data, file.getAbsolutePath());
		sendFile(resp, file, "inout_log.csv", "text/csv");
		
	}
	
	private void exportAlertCsv(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		LocalDate from = parseDate(req.getParameter("from"));
		LocalDate to = parseDate(req.getParameter("to"));
		
		List<AlertHistory> data = exportService.getAlertHistory(from, to);
		
		File file = File.createTempFile("alert_history_", ".csv");
		fileExportService.exportAlertHistoryCsv(data, file.getAbsolutePath());
		sendFile(resp, file, "alert_history.csv", "text/csv");
	}
	
	private void exportStatisticsCsv(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		StatisticsReport report = createStatisticsReport(req);
		
		File file = File.createTempFile("statistics_", ".csv");
		fileExportService.exportStatisticsCsv(report, file.getAbsolutePath());
		sendFile(resp, file, "statistics.csv", "text/csv");
	}
	
	private void exportInOutExcel(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		LocalDate from = parseDate(req.getParameter("from"));
		LocalDate to = parseDate(req.getParameter("to"));
		
		List<InOutLog> data = exportService.getInOutboundLog(from, to);
		
		File file = File.createTempFile("inout_log_", ".xlsx");
		fileExportService.exportInOutLogExcel(data, file.getAbsolutePath());
		sendFile(resp, file, "inout_log.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		
	}
	
	private void exportAlertExcel(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		LocalDate from = parseDate(req.getParameter("from"));
		LocalDate to = parseDate(req.getParameter("to"));
		
		List<AlertHistory> data = exportService.getAlertHistory(from, to);
		
		File file = File.createTempFile("alert_history_", ".xlsx");
		fileExportService.exportAlertHistoryExcel(data, file.getAbsolutePath());
		sendFile(resp, file, "alert_history.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
	}
	
	private void exportStatisticsExcel(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		StatisticsReport report = createStatisticsReport(req);
		
		File file = File.createTempFile("statistics_", ".xlsx");
		fileExportService.exportStatisticsExcel(report, file.getAbsolutePath());
		sendFile(resp, file, "statistics.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
	}
	
	private void exportDailyPdf(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		LocalDate date = parseDate(req.getParameter("date"));
		
		DailyReport report = dailyReportService.getDailyReport(date);
		
		// 로그인 한 사람의 이름
		HttpSession session = req.getSession(false);
		String opName = "-";
		if(session != null) {
			String loginName = (String) session.getAttribute("name");
			
			if(loginName != null && !loginName.isBlank()) opName = loginName;
		}
		
		File file = File.createTempFile("daily_report_", ".pdf");
		fileExportService.exportDailyReportPdf(report, opName, file.getAbsolutePath());
		sendFile(resp, file, "daily_report.pdf", "application/pdf");
	}
	
	private StatisticsReport createStatisticsReport(HttpServletRequest req) {
		String unit = req.getParameter("unit");
		LocalDate from = parseDate(req.getParameter("from"));
		LocalDate to = parseDate(req.getParameter("to"));
		String start = req.getParameter("start");
		String end = req.getParameter("end");
		
		List<PeriodInOutStat> total = statisticsService.getInOutStatistics(unit, from, to, "전체");
		List<PeriodInOutStat> large = statisticsService.getInOutStatistics(unit, from, to, "대형");
		List<PeriodInOutStat> medium = statisticsService.getInOutStatistics(unit, from, to, "중형");
		List<PeriodInOutStat> small = statisticsService.getInOutStatistics(unit, from, to, "소형");
		
		List<StockTurnover> turnover = statisticsService.getStockTurnoverStatistics();
		List<ClientOutboundRanking> rank = statisticsService.getTop5Outbound(from, to);
		List<ClientMonthlyTrend> trend = statisticsService.getMonthlyTrend(start, end);
		
		StatisticsReport report = new StatisticsReport();
		report.setTotalInOut(total);
		report.setLargeInOut(large);
		report.setMediumInOut(medium);
		report.setSmallInOut(small);

		report.setStockTurnover(turnover);
		report.setClientRanking(rank);
		report.setClientMonthlyTrend(trend);
		
		return report;
	}
	
	private static LocalDate parseDate(String value) {
		if(value == null || value.isBlank()) return null; 
		
		return LocalDate.parse(value);
	}
	
	private void sendFile(HttpServletResponse resp, File file, String downloadName, String contentType) throws IOException{
		resp.setStatus(200);
		resp.setContentType(contentType);
		resp.setHeader("Content-Disposition", "attachment; filename=\"" + downloadName + "\"");
		resp.setContentLengthLong(file.length());
		
		try (FileInputStream fis = new FileInputStream(file); OutputStream out = resp.getOutputStream()){
			byte[] buffer = new byte[8192];
			int length;
			
			while((length = fis.read(buffer)) != -1) {
				out.write(buffer, 0, length);
			}
			
			out.flush();
		} finally {
			if(file.exists()) file.delete();
		}
	}
}
