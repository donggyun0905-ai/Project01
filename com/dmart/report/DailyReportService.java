package com.dmart.report;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.dmart.db.DBConnection;
import com.dmart.report.dao.DailyReportDao;
import com.dmart.report.dto.DailyComparison;
import com.dmart.report.dto.DailyReport;
import com.dmart.report.dto.LowStockItem;
import com.dmart.report.dto.TopOutboundItem;

public class DailyReportService {
	private final DailyReportDao dao = new DailyReportDao();

	// 조회일과 전일의 입출고량을 비교하여 증감량/증감률 계산
	public DailyComparison getDailyComparison(LocalDate searchDate) {
		
		// 조회일 없으면 오늘 날짜 기본값
		if (searchDate == null)
			searchDate = LocalDate.now();

		try (Connection conn = DBConnection.getConnection()) {
			DailyComparison dailyComp = dao.selectDailyComparison(conn, searchDate);

			int tdyInbound = dailyComp.getTodayInboundQty();
			int ydayInbound = dailyComp.getYesterdayInboundQty();
			int tdyOutbound = dailyComp.getTodayOutboundQty();
			int ydayOutbound = dailyComp.getYesterdayOutboundQty();

			// 당일 - 전일 기준 증감량 계산
			int inboundChange = tdyInbound - ydayInbound;
			int outboundChange = tdyOutbound - ydayOutbound;

			// 전일 수량이 0이면 증감률 계산이 불가능 => null 처리
			// 화면에서 null을 '-'로 표시
			Double inboundChangeRate = (ydayInbound != 0) ? inboundChange / (double) ydayInbound * 100 : null;
			Double outboundChangeRate = (ydayOutbound != 0) ? outboundChange / (double) ydayOutbound * 100 : null;

			dailyComp.setDate(searchDate);
			dailyComp.setInboundQtyChange(inboundChange);
			dailyComp.setOutboundQtyChange(outboundChange);
			dailyComp.setInboundQtyChangeRate(inboundChangeRate);
			dailyComp.setOutboundQtyChangeRate(outboundChangeRate);
			dailyComp.setInboundByUnit(dao.selectTodayInboundByUnit(conn, searchDate));
			dailyComp.setOutboundByUnit(dao.selectTodayOutboundByUnit(conn, searchDate));

			return dailyComp;
			
		} catch (SQLException e) {
			throw new RuntimeException("일일 입출고 비교 조회 중 DB 오류 발생", e);
		}
	}
	
	// 현재 재고가 최소 기준 이하인 재고 부족 품목 조회
	public List<LowStockItem> getLowStockitems() {
		try (Connection conn = DBConnection.getConnection()) {
			return dao.selectLowStockItems(conn, "재고부족");
		} catch (SQLException e) {
			throw new RuntimeException("재고 부족 품목 조회 중 DB 오류 발생", e);
		}
	}
	
	// 조회일 기준 출고량 TOP5 품목 조회
	public List<TopOutboundItem> getTop5OutboundItems(LocalDate searchDate) {
	    if (searchDate == null) searchDate = LocalDate.now();
	    
	    try (Connection conn = DBConnection.getConnection()) {
	    	return dao.selectTop5OutboundItems(conn, searchDate);
		} catch (SQLException e) {
			throw new RuntimeException("출고 TOP5 조회 중 DB 오류 발생", e);
		}
	}
	
	// 전체 데이터 묶음 => 일일보고서 PDF 내보내기
	public DailyReport getDailyReport(LocalDate searchDate) {
		if(searchDate == null) searchDate = LocalDate.now();
		
		DailyComparison comparison = getDailyComparison(searchDate);

	    List<LowStockItem> lowStockItems = getLowStockitems();

	    List<TopOutboundItem> topOutboundItems = getTop5OutboundItems(searchDate);

	    return new DailyReport(searchDate, comparison, lowStockItems, topOutboundItems
	    );
	}
}
