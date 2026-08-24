package com.dmart.report;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.Font;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;

import com.dmart.report.dto.AlertHistory;
import com.dmart.report.dto.ClientMonthlyTrend;
import com.dmart.report.dto.ClientOutboundRanking;
import com.dmart.report.dto.DailyComparison;
import com.dmart.report.dto.DailyReport;
import com.dmart.report.dto.InOutLog;
import com.dmart.report.dto.LowStockItem;
import com.dmart.report.dto.PeriodInOutStat;
import com.dmart.report.dto.StatisticsReport;
import com.dmart.report.dto.StockTurnover;
import com.dmart.report.dto.TopOutboundItem;
import com.opencsv.CSVWriter;

public class FileExportService {
	
	/*
	 * 데이터 내보내기(입출고 로그 / 알림 이력 / 통계 보고서) - CSV
	 */	
	public void exportInOutLogCsv(List<InOutLog> data, String filePath) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(filePath);
				Writer writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

			writer.write('\uFEFF');

			try (CSVWriter csvWriter = new CSVWriter(writer)) {
				String[] header = { "Lot ID", "품목 ID", "품목명", "구분", "수량", "거래처명", "처리자", "처리일" };

				csvWriter.writeNext(header);
				for (InOutLog log : data) {
					String[] row = { 
							String.valueOf(log.getLotId()), 
							String.valueOf(log.getItemId()), 
							log.getItemName(),
							log.getType(), 
							String.valueOf(log.getQuantity()), 
							log.getPartnerName(), 
							log.getOpName(),
							String.valueOf(log.getProcessedAt()) 
						};
					csvWriter.writeNext(row);
				}
			}
		}
	}
	
	public void exportAlertHistoryCsv(List<AlertHistory> data, String filePath) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(filePath);
				Writer writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

			writer.write('\uFEFF');

			try (CSVWriter csvWriter = new CSVWriter(writer)) {
				String[] header = { "Alert ID", "품목 ID", "품목명", "유형", "메시지", "해결 여부", "발생일" };

				csvWriter.writeNext(header);
				for (AlertHistory alert : data) {
					String[] row = { 
							String.valueOf(alert.getAlertId()), 
							String.valueOf(alert.getItemId()), 
							alert.getItemName(),
							alert.getAlertType(), 
							alert.getMessage(), 
							String.valueOf(alert.getIsResolved()),
							String.valueOf(alert.getCreatedAt()) 
						};
					csvWriter.writeNext(row);
				}
			}
		}
	}
	
	public void exportStatisticsCsv(StatisticsReport report, String filePath) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(filePath);
				Writer writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

			writer.write('\uFEFF');

			try (CSVWriter csvWriter = new CSVWriter(writer)) {
				csvWriter.writeNext(new String[] {"[입출고량 집계]"});
				csvWriter.writeNext(new String[] {
						"기간", "전체 입고량", "전체 출고량", "대형 입고량", "대형 출고량",
				        "중형 입고량", "중형 출고량", "소형 입고량", "소형 출고량"
				});
				
				List<PeriodInOutStat> total = report.getTotalInOut();
				List<PeriodInOutStat> large = report.getLargeInOut();
				List<PeriodInOutStat> medium = report.getMediumInOut();
				List<PeriodInOutStat> small = report.getSmallInOut();
				
				for (int i = 0; i < total.size(); i++) {
					PeriodInOutStat totalStat = total.get(i);
					PeriodInOutStat largeStat = large.get(i);
					PeriodInOutStat mediumStat = medium.get(i);
					PeriodInOutStat smallStat = small.get(i);
					
					String[] row = {
							String.valueOf(totalStat.getPeriod()),
							String.valueOf(totalStat.getInboundQty()),
							String.valueOf(totalStat.getOutboundQty()),
							
							String.valueOf(largeStat.getInboundQty()),
							String.valueOf(largeStat.getOutboundQty()),
							
							String.valueOf(mediumStat.getInboundQty()),
							String.valueOf(mediumStat.getOutboundQty()),
							
							String.valueOf(smallStat.getInboundQty()),
							String.valueOf(smallStat.getOutboundQty())
					};
					csvWriter.writeNext(row);
				}
				
				csvWriter.writeNext(new String[] {});
				
				csvWriter.writeNext(new String[] {"재고 회전율"});
				csvWriter.writeNext(new String[] {
						"품목 ID", "품목명", "입고일", "현재 재고량", "일평균 소진량", "회전율", "상태"
				});				
				
				for (StockTurnover stat : report.getStockTurnover()) {
					String[] row = {
							String.valueOf(stat.getItemId()),
							stat.getItemName(),
							String.valueOf(stat.getInboundDate()),
							String.valueOf(stat.getCurrentStockQty()),
							String.valueOf(stat.getDailyVelocity()),
							String.valueOf(stat.getTurnoverRatio()),
							stat.getStatus()
					};
					csvWriter.writeNext(row);
				}
				
				csvWriter.writeNext(new String[] {});
				
				csvWriter.writeNext(new String[] {"거래처 출고 랭킹"});
				csvWriter.writeNext(new String[] {
						"순위", "거래처 ID", "거래처명", "총 출고량"
				});
				
				for (ClientOutboundRanking stat : report.getClientRanking()) {
					String[] row = {
							String.valueOf(stat.getRank()),
							String.valueOf(stat.getPartnerId()),
							stat.getPartnerName(),
							String.valueOf(stat.getTotalQty())
					};
					csvWriter.writeNext(row);
				}
				
				csvWriter.writeNext(new String[] {});
				
				csvWriter.writeNext(new String[] {"거래처 월별 추이"});
				csvWriter.writeNext(new String[] {
						"거래처 ID", "거래처명", "년 월", "월 출고량", "건수"
				});
				
				for (ClientMonthlyTrend stat : report.getClientMonthlyTrend()) {
					String[] row = {
							String.valueOf(stat.getPartnerId()),
							stat.getPartnerName(),
							stat.getYearMonth(),
							String.valueOf(stat.getMonthlyQty()),
							String.valueOf(stat.getTransactionCount())
					};
					csvWriter.writeNext(row);
				}
			}
		}
	}
	
	/*
	 * 데이터 내보내기(입출고 로그 / 알림 이력 / 통계 보고서) - Excel
	 */
	public void exportInOutLogExcel(List<InOutLog> data, String filePath) throws IOException {
		try (Workbook workbook = new XSSFWorkbook()){
			Sheet sheet = workbook.createSheet("입출고 로그");

			String[] header = { "Lot ID", "품목 ID", "품목명", "구분", "수량", "거래처명", "처리자", "처리일" };

			Row headerRow = sheet.createRow(0);

			for (int i = 0; i < header.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(header[i]);
			}

			int rowNum = 1;

			for (InOutLog log : data) {
				Row row = sheet.createRow(rowNum++);

				row.createCell(0).setCellValue(log.getLotId());
				row.createCell(1).setCellValue(log.getItemId());
				row.createCell(2).setCellValue(log.getItemName());
				row.createCell(3).setCellValue(log.getType());
				row.createCell(4).setCellValue(log.getQuantity());
				row.createCell(5).setCellValue(log.getPartnerName());
				row.createCell(6).setCellValue(log.getOpName());
				row.createCell(7).setCellValue(log.getProcessedAt() != null ? log.getProcessedAt().toString() : "");
			}

			for (int i = 0; i < header.length; i++) {
				sheet.autoSizeColumn(i);
			}
			
			try (FileOutputStream fos = new FileOutputStream(filePath)) {
				workbook.write(fos);
			} 
		}
	}

	public void exportAlertHistoryExcel(List<AlertHistory> data, String filePath) throws IOException {
		try (Workbook workbook = new XSSFWorkbook()){
			Sheet sheet = workbook.createSheet("알림 이력");

			String[] header = { "Alert ID", "품목 ID", "품목명", "유형", "메시지", "해결 여부", "발생일" };

			Row headerRow = sheet.createRow(0);

			for (int i = 0; i < header.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(header[i]);
			}

			int rowNum = 1;

			for (AlertHistory alert : data) {
				Row row = sheet.createRow(rowNum++);

				row.createCell(0).setCellValue(alert.getAlertId());
				row.createCell(1).setCellValue(alert.getItemId());
				row.createCell(2).setCellValue(alert.getItemName());
				row.createCell(3).setCellValue(alert.getAlertType());
				row.createCell(4).setCellValue(alert.getMessage());
				row.createCell(5).setCellValue(alert.getIsResolved());
				row.createCell(6).setCellValue(alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : "");
			}

			for (int i = 0; i < header.length; i++) {
				sheet.autoSizeColumn(i);
			}
			
			try (FileOutputStream fos = new FileOutputStream(filePath)) {
				workbook.write(fos);
			} 
		}
	}

	public void exportStatisticsExcel(StatisticsReport report, String filePath) throws IOException {
		try (Workbook workbook = new XSSFWorkbook()) {
			createInOutSheet (workbook, report);
			createStockTurnoverSheet(workbook, report);
			createClientRankingSheet(workbook, report);
			createClientMonthlyTrendSheet(workbook, report);
			
			try (FileOutputStream fos = new FileOutputStream(filePath)) {
				workbook.write(fos);
			} 
		}
	}
	
	// 입출고량 집계 Sheet
	private void createInOutSheet(Workbook workbook, StatisticsReport report) {
		Sheet sheet = workbook.createSheet("입출고량 집계");
		
		String[] header = { "기간", "전체 입고량", "전체 출고량", "대형 입고량", "대형 출고량",
		        "중형 입고량", "중형 출고량", "소형 입고량", "소형 출고량" };
		
		Row headerRow = sheet.createRow(0);
		
		for(int i = 0; i < header.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(header[i]);
		}
		
		List<PeriodInOutStat> total = report.getTotalInOut();
		List<PeriodInOutStat> large = report.getLargeInOut();
		List<PeriodInOutStat> medium = report.getMediumInOut();
		List<PeriodInOutStat> small = report.getSmallInOut();
		
		int rowNum = 1;
		
		for (int i = 0; i < total.size(); i++) {
			PeriodInOutStat totalStat = total.get(i);
			PeriodInOutStat largeStat = large.get(i);
			PeriodInOutStat mediumStat = medium.get(i);
			PeriodInOutStat smallStat = small.get(i);
			
			Row row = sheet.createRow(rowNum++);
			
			row.createCell(0).setCellValue(totalStat.getPeriod());
			
			row.createCell(1).setCellValue(totalStat.getInboundQty());
			row.createCell(2).setCellValue(totalStat.getOutboundQty());
			
			row.createCell(3).setCellValue(largeStat.getInboundQty());
			row.createCell(4).setCellValue(largeStat.getOutboundQty());
			
			row.createCell(5).setCellValue(mediumStat.getInboundQty());
			row.createCell(6).setCellValue(mediumStat.getOutboundQty());
			
			row.createCell(7).setCellValue(smallStat.getInboundQty());
			row.createCell(8).setCellValue(smallStat.getOutboundQty());
		}
		
		for (int i = 0; i < header.length; i++) {
			sheet.autoSizeColumn(i);
		}
	}
	
	// 재고 회전율 Sheet
	private void createStockTurnoverSheet(Workbook workbook, StatisticsReport report) {
		Sheet sheet = workbook.createSheet("재고 회전율");
		
		String[] header = { "품목 ID", "품목명", "입고일", "현재 재고량", "일평균 소진량", "회전율", "상태" };
		
		Row headerRow = sheet.createRow(0);
		
		for(int i = 0; i < header.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(header[i]);
		}
		
		int rowNum = 1;
		
		for (StockTurnover stat : report.getStockTurnover()) {
			Row row = sheet.createRow(rowNum++);
			
			row.createCell(0).setCellValue(stat.getItemId());
			row.createCell(1).setCellValue(stat.getItemName());
			row.createCell(2).setCellValue(stat.getInboundDate() != null ? stat.getInboundDate().toString() : "");
			row.createCell(3).setCellValue(stat.getCurrentStockQty());
			row.createCell(4).setCellValue(stat.getDailyVelocity() != null ? stat.getDailyVelocity().doubleValue() : 0);
			row.createCell(5).setCellValue(stat.getTurnoverRatio() != null ? stat.getTurnoverRatio().doubleValue() : 0);
			row.createCell(6).setCellValue(stat.getStatus() != null ? stat.getStatus() : "" );
		}
		
		for (int i = 0; i < header.length; i++) {
			sheet.autoSizeColumn(i);
		}
	}
	
	// 거래처 출고 랭킹
	private void createClientRankingSheet(Workbook workbook, StatisticsReport report) {
		Sheet sheet = workbook.createSheet("거래처 출고 랭킹 Top5");

		String[] header = { "순위", "거래처 ID", "거래처명", "총 출고량" };

		Row headerRow = sheet.createRow(0);

		for (int i = 0; i < header.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(header[i]);
		}

		int rowNum = 1;

		for (ClientOutboundRanking stat : report.getClientRanking()) {
			Row row = sheet.createRow(rowNum++);

			row.createCell(0).setCellValue(stat.getRank());
			row.createCell(1).setCellValue(stat.getPartnerId());
			row.createCell(2).setCellValue(stat.getPartnerName());
			row.createCell(3).setCellValue(stat.getTotalQty());
		}

		for (int i = 0; i < header.length; i++) {
			sheet.autoSizeColumn(i);
		}
	}
	
	// 거래처 월별 추이
	private void createClientMonthlyTrendSheet(Workbook workbook, StatisticsReport report) {
		Sheet sheet = workbook.createSheet("거래처 월별 추이");

		String[] header = { "거래처 ID", "거래처명", "년 월", "월 출고량", "건수" };

		Row headerRow = sheet.createRow(0);

		for (int i = 0; i < header.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(header[i]);
		}

		int rowNum = 1;

		for (ClientMonthlyTrend stat : report.getClientMonthlyTrend()) {
			Row row = sheet.createRow(rowNum++);

			row.createCell(0).setCellValue(stat.getPartnerId());
			row.createCell(1).setCellValue(stat.getPartnerName());
			row.createCell(2).setCellValue(stat.getYearMonth());
			row.createCell(3).setCellValue(stat.getMonthlyQty());
			row.createCell(4).setCellValue(stat.getTransactionCount());
		}

		for (int i = 0; i < header.length; i++) {
			sheet.autoSizeColumn(i);
		}
	}
	
	// 일일보고서 - PDF
	public void exportDailyReportPdf(DailyReport report, String opName, String filePath) throws IOException, DocumentException {
		Document document = new Document();
		
		BaseFont baseFont = BaseFont.createFont(
				"fonts/Pretendard-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED
		);
		
		Font textFont = new Font(baseFont, 10);
		Font subFont = new Font(baseFont, 13, Font.NORMAL);
		Font titleFont = new Font(baseFont, 20, Font.BOLD);
		
		try {
			PdfWriter.getInstance(document, new FileOutputStream(filePath));
			document.open();
			
			Paragraph title = new Paragraph("DOWN MART 일일 보고서", titleFont);
			title.setAlignment(Element.ALIGN_CENTER);
			title.setSpacingAfter(10f);
			document.add(title);
			
			String printedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			
			Paragraph info = new Paragraph("조회일: " + report.getDate() +
					"\n출력 일시: " + printedAt +
					"\n출력자: " + (opName != null ? opName : "-"), textFont);
			info.setAlignment(Element.ALIGN_RIGHT);
			info.setSpacingAfter(15f);
			document.add(info);
			
			Paragraph section1 = new Paragraph("1. 전일 대비 증감", subFont);
			section1.setSpacingAfter(10f);
			section1.setSpacingAfter(6f);
			document.add(section1);
			
			DailyComparison comparison = report.getDailyComp();
			
			if(comparison != null) {
				PdfPTable table = new PdfPTable(4);
				table.setWidthPercentage(100);
				table.setWidths(new float[] {1.5f, 1.5f, 1.5f, 1.5f});
				
				table.addCell(createHeaderCell("구분", textFont));
				table.addCell(createHeaderCell("금일", textFont));
				table.addCell(createHeaderCell("전일", textFont));
				table.addCell(createHeaderCell("증감률", textFont));
				
				table.addCell(createCentercell("입고", textFont));
				table.addCell(createCell(String.valueOf(comparison.getTodayInboundQty()), textFont));
				table.addCell(createCell(String.valueOf(comparison.getYesterdayInboundQty()), textFont));
				table.addCell(createCell(formatRate(comparison.getInboundQtyChangeRate()), textFont));
				
				table.addCell(createCentercell("출고", textFont));
				table.addCell(createCell(String.valueOf(comparison.getTodayOutboundQty()), textFont));
				table.addCell(createCell(String.valueOf(comparison.getYesterdayOutboundQty()), textFont));
				table.addCell(createCell(formatRate(comparison.getOutboundQtyChangeRate()), textFont));
				
				document.add(table);
			}
			document.add(new Paragraph(" "));
			
			Paragraph section2 = new Paragraph("2. 재고 부족 품목", subFont);
			section2.setSpacingAfter(10f);
			section2.setSpacingAfter(6f);
			document.add(section2);
			
			PdfPTable lowStockTable = new PdfPTable(4);
			lowStockTable.setWidthPercentage(100);
			lowStockTable.setWidths(new float[] {1.2f, 3f, 1.5f, 1.5f});
			
			lowStockTable.addCell(createHeaderCell("품목 ID", textFont));
			lowStockTable.addCell(createHeaderCell("품목 이름", textFont));
			lowStockTable.addCell(createHeaderCell("현재 수량", textFont));
			lowStockTable.addCell(createHeaderCell("최소 수량", textFont));
			
			for (LowStockItem item : report.getLowStockItem()) {
				lowStockTable.addCell(createCell(String.valueOf(item.getItemId()), textFont));
				lowStockTable.addCell(createCell(item.getItemName(), textFont));
				lowStockTable.addCell(createCell(String.valueOf(item.getCurrentQty()), textFont));
				lowStockTable.addCell(createCell(String.valueOf(item.getMinQty()), textFont));
			}
			
			document.add(lowStockTable);
			document.add(new Paragraph(" "));
			
			Paragraph section3 = new Paragraph("3. 출고량 Top5", subFont);
			section3.setSpacingAfter(10f);
			section3.setSpacingAfter(6f);
			document.add(section3);
			
			PdfPTable topTable = new PdfPTable(4);
			topTable.setWidthPercentage(100);
			topTable.setWidths(new float[] {1f, 1.5f, 3f, 1.5f});
			
			topTable.addCell(createHeaderCell("순위", textFont));
			topTable.addCell(createHeaderCell("품목 ID", textFont));
			topTable.addCell(createHeaderCell("품목 이름", textFont));
			topTable.addCell(createHeaderCell("출고량", textFont));
			
			for (TopOutboundItem item : report.getTopOutItem()) {
				topTable.addCell(createCell(String.valueOf(item.getRank()), textFont));
				topTable.addCell(createCell(String.valueOf(item.getItemId()), textFont));
				topTable.addCell(createCell(item.getItemName(), textFont));
				topTable.addCell(createCell(String.valueOf(item.getTotalOutboundQty()), textFont));
			}
			document.add(topTable);
			
		}finally {
			if(document.isOpen()) {
				document.close();
			}
		}
	}
	
	private String formatRate(Double rate) {
		if(rate == null) return "-";
		
		return String.format("%.2f%%", rate);
	}
	
	private PdfPCell createHeaderCell(String text, Font font) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font));
		
		cell.setPadding(6f);
		cell.setMinimumHeight(24f);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		
		return cell;
	}
	
	private PdfPCell createCentercell(String text, Font font) {
		PdfPCell cell = createCell(text, font);
		
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		
		return cell;
	}
	
	private PdfPCell createCell(String text, Font font) {
		PdfPCell cell = new PdfPCell(new Paragraph(text != null ? text : "", font));
		
		cell.setPadding(5f);
		cell.setMinimumHeight(22f);
		cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		
		return cell;
	}
}
