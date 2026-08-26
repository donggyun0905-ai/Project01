package com.dmart.report;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import com.dmart.db.DBConnection;
import com.dmart.report.dao.ExportDao;
import com.dmart.report.dao.StatisticsDao;
import com.dmart.report.dto.AlertHistory;
import com.dmart.report.dto.InOutLog;
import com.dmart.report.dto.ItemExportRow;

public class ExportService {

	private final ExportDao dao = new ExportDao();
	private final StatisticsDao statisticsDao = new StatisticsDao();
	
	// 입출고 로그
	public List<InOutLog> getInOutboundLog(LocalDate from, LocalDate to){
		if(to == null) to = LocalDate.now();
		if(from == null) from = to.minusDays(29);
		if(from.isAfter(to)) throw new IllegalArgumentException("시작일은 종료일보다 이후일 수 없습니다.");
		
		try (Connection conn = DBConnection.getConnection()){
			return dao.selectInOutboundLog(conn, from, to);
		} catch (SQLException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	// 알림 이력
	public List<AlertHistory> getAlertHistory(LocalDate from, LocalDate to){
		if(to == null) to = LocalDate.now();
		if(from == null) from = to.minusDays(29);
		if(from.isAfter(to)) throw new IllegalArgumentException("시작일은 종료일보다 이후일 수 없습니다.");
		
		try(Connection conn = DBConnection.getConnection()) {
			return dao.selectAlertHistory(conn, from, to);
		} catch (SQLException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}

	// 품목 데이터 엑셀화 - 활성 품목 전체(품목번호/품목명/카테고리/단위/유통기한/총재고/회전율/입고량/출고량)
	public List<ItemExportRow> getItemExportRows() {
		try (Connection conn = DBConnection.getConnection()) {
			return statisticsDao.selectItemExportRows(conn);
		} catch (SQLException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}

}
