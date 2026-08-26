package com.dmart.report.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dmart.report.dto.ClientMonthlyTrend;
import com.dmart.report.dto.ClientOutboundRanking;
import com.dmart.report.dto.ItemExportRow;
import com.dmart.report.dto.StockTurnover;

public class StatisticsDao {

	private static final double DEAD_STOCK_THRESHOLD = 0.2;
	
	// PERIOD FORMAT
	private String dateFormat(String unit) {
		switch (unit) {
		case "week":
			return "%x-%v";
		case "month":
			return "%Y-%m";
		default:
			return "%Y-%m-%d";
		}
	}

	// 기간별 입고량
	public Map<String, Integer> selectInboundByPeriod(Connection conn, String unit, LocalDate from, LocalDate to, String warehouseName) throws SQLException {
		StringBuilder sql = new StringBuilder(
				"SELECT DATE_FORMAT(s.inbound_date, ?) AS period, SUM(s.initial_quantity) AS qty " 
				+ "FROM stock_lot s ");
		if (warehouseName != null)
			sql.append("JOIN zone z ON z.zone_id = s.zone_id "
					+ "JOIN warehouse w ON w.warehouse_id = z.warehouse_id ");
		
		sql.append("WHERE s.inbound_date BETWEEN ? AND ? ");
		
		if (warehouseName != null)
			sql.append("AND w.name = ? ");
		
		sql.append("GROUP BY period ORDER BY period ASC");
		
		return executeGrouped(conn, sql.toString(), dateFormat(unit), from, to, warehouseName);
	}
	
	// 기간별 출고량
	public Map<String, Integer> selectOutboundByPeriod(Connection conn, String unit, LocalDate from, LocalDate to, String warehouseName) throws SQLException {
		StringBuilder sql = new StringBuilder(
				"SELECT DATE_FORMAT(o.outbound_date, ?) AS period, SUM(o.quantity) AS qty "
				+ "FROM outbound o "
				+ "JOIN stock_lot s ON s.lot_id = o.lot_id ");
		if(warehouseName != null)
			sql.append("JOIN zone z ON z.zone_id = s.zone_id "
					+ "JOIN warehouse w ON w.warehouse_id = z.warehouse_id ");
		
		sql.append("WHERE o.outbound_date BETWEEN ? AND ? ");
		
		if(warehouseName != null)
			sql.append("AND w.name = ? ");
		
		sql.append("GROUP BY period ORDER BY period ASC");
		
		return executeGrouped(conn, sql.toString(), dateFormat(unit), from, to, warehouseName);
	}
	
	// 입고/출고 공통 처리
	private Map<String, Integer> executeGrouped(Connection conn, String sql, String fmt, LocalDate from, LocalDate to, String warehouseName) throws SQLException {
		Map<String, Integer> result = new LinkedHashMap<>();
		
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int idx = 1;
            pstmt.setString(idx++, fmt); // format
            pstmt.setDate(idx++, java.sql.Date.valueOf(from));
            pstmt.setDate(idx++, java.sql.Date.valueOf(to));
            if (warehouseName != null) pstmt.setString(idx++, warehouseName);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("period"), rs.getInt("qty"));
                }
            }
        }
        return result;
	}
	
	// 재고 회전율 분석 => 6개 정도의 목록만
	public List<StockTurnover> selectTurnoverRatio(Connection conn) throws SQLException {
		/*
		 * 일평균 소진 속도 = 최근 90일 총 출고량 / 90
		 * 간이 회전율 = 최근 90일 총 출고량 / 현재 재고량
		 * 
		 * 현재 DB에서 기간 평균 재고량 산출이 복잡해 간이 회전율로 계산
		 */
		String sql = "SELECT i.item_id, i.item_name, st.current_stock_qty, st.first_inbound_date, "
				+ "COALESCE(ob.total_outbound, 0) AS total_outbound, "
				+ "ROUND(COALESCE(ob.total_outbound, 0) / 90.0, 2) AS daily_velocity, " // 일평균 소진 속도
				+ "ROUND(COALESCE(ob.total_outbound, 0) / NULLIF(st.current_stock_qty, 0), 2) AS turnover_ratio, " // 간이 재고 회전율
				+ "CASE WHEN COALESCE(ob.total_outbound, 0) = 0 THEN 'DEAD_STOCK' " // 최근 90일 출고 이력 X
				+ "WHEN (COALESCE(ob.total_outbound, 0) / NULLIF(st.current_stock_qty, 0)) < ? THEN 'DEAD_STOCK' " // 간이 회전율 임계치 보다 낮은 경우
				+ "ELSE 'NORMAL' END AS status " 
				+ "FROM item i "
				+ "JOIN (SELECT item_id, SUM(quantity) AS current_stock_qty, "
				+ "MIN(inbound_date) AS first_inbound_date "
				+ "FROM stock_lot "
				+ "WHERE quantity > 0 "
				+ "GROUP BY item_id "
				+ ") st ON st.item_id = i.item_id " // JOIN: 현재 재고량 
				+ "LEFT JOIN (SELECT s.item_id, SUM(o.quantity) AS total_outbound "
				+ "FROM outbound o "
				+ "JOIN stock_lot s ON s.lot_id = o.lot_id "
				+ "WHERE o.outbound_date >= DATE_SUB(CURDATE(), INTERVAL 90 DAY) "
				+ "GROUP BY s.item_id "
				+ ") ob ON ob.item_id = i.item_id " // LEFT JOIN: 최근 90일 출고량
				+ "ORDER BY turnover_ratio DESC"; 
		
		List<StockTurnover> result = new ArrayList<>();
		
		try (PreparedStatement pstmt = conn.prepareStatement(sql)){
			pstmt.setDouble(1, DEAD_STOCK_THRESHOLD);
			
			try (ResultSet rs = pstmt.executeQuery()){
				while(rs.next()) {
					StockTurnover dto = new StockTurnover();
					dto.setItemId(rs.getLong("item_id"));
					dto.setItemName(rs.getString("item_name"));
					dto.setCurrentStockQty(rs.getInt("current_stock_qty"));
					
					java.sql.Date inboundDate = rs.getDate("first_inbound_date");
					dto.setInboundDate(inboundDate != null ? inboundDate.toLocalDate() : null);
					
					dto.setDailyVelocity(rs.getBigDecimal("daily_velocity"));
					dto.setTurnoverRatio(rs.getBigDecimal("turnover_ratio"));
					dto.setStatus(rs.getString("status"));
					
					result.add(dto);
				}
			} 
		}
		return result;
	}

	// 품목 데이터 엑셀 내보내기용 - 활성 품목 전체를 대상으로 현재재고/누적입고/누적출고/회전율을
	// 한 번에 모아 온다. selectTurnoverRatio()는 현재재고가 있는(quantity > 0) 품목만 INNER JOIN
	// 하지만, 여기서는 내보내기 목록에서 품목이 통째로 빠지면 안 되므로 전부 LEFT JOIN한다.
	// 누적입고는 parent_lot_id IS NULL(재고이동/반품/폐기로 분할된 로트 제외)만 세서 이중계산을 피한다
	// (DailyReportDao.selectDailyComparison과 같은 기준).
	public List<ItemExportRow> selectItemExportRows(Connection conn) throws SQLException {
		String sql = "SELECT i.item_id, i.item_name, i.category, i.unit, i.shelf_life_days, "
				+ "COALESCE(st.current_stock_qty, 0) AS current_stock_qty, "
				+ "COALESCE(ib.total_inbound, 0) AS total_inbound, "
				+ "COALESCE(ob.total_outbound, 0) AS total_outbound, "
				+ "ROUND(COALESCE(ob.total_outbound, 0) / NULLIF(COALESCE(st.current_stock_qty, 0), 0), 2) AS turnover_ratio "
				+ "FROM item i "
				+ "LEFT JOIN (SELECT item_id, SUM(quantity) AS current_stock_qty "
				+ "FROM stock_lot WHERE status = 'NORMAL' GROUP BY item_id"
				+ ") st ON st.item_id = i.item_id "
				+ "LEFT JOIN (SELECT item_id, SUM(initial_quantity) AS total_inbound "
				+ "FROM stock_lot WHERE parent_lot_id IS NULL GROUP BY item_id"
				+ ") ib ON ib.item_id = i.item_id "
				+ "LEFT JOIN (SELECT s.item_id, SUM(o.quantity) AS total_outbound "
				+ "FROM outbound o JOIN stock_lot s ON s.lot_id = o.lot_id GROUP BY s.item_id"
				+ ") ob ON ob.item_id = i.item_id "
				+ "WHERE i.is_active = TRUE "
				+ "ORDER BY i.item_id";

		List<ItemExportRow> result = new ArrayList<>();

		try (PreparedStatement pstmt = conn.prepareStatement(sql);
				ResultSet rs = pstmt.executeQuery()) {
			while (rs.next()) {
				ItemExportRow row = new ItemExportRow();
				row.setItemId(rs.getLong("item_id"));
				row.setItemName(rs.getString("item_name"));
				row.setCategory(rs.getString("category"));
				row.setUnit(rs.getString("unit"));
				int shelfLifeDays = rs.getInt("shelf_life_days");
				row.setShelfLifeDays(rs.wasNull() ? null : shelfLifeDays);
				row.setTotalStock(rs.getInt("current_stock_qty"));
				row.setInboundQty(rs.getInt("total_inbound"));
				row.setOutboundQty(rs.getInt("total_outbound"));
				java.math.BigDecimal ratio = rs.getBigDecimal("turnover_ratio");
				row.setTurnoverRatio(ratio != null ? ratio.doubleValue() : null);
				result.add(row);
			}
		}
		return result;
	}

	// 거래처별 출고 실적 Top5 조회
	public List<ClientOutboundRanking> selectTop5Outbound(Connection conn, LocalDate from, LocalDate to) throws SQLException {
		String sql = "SELECT p.partner_id, p.name, SUM(o.quantity) AS total_outbound " 
				+ "FROM outbound o "
				+ "JOIN partner p ON o.partner_id = p.partner_id " 
				+ "WHERE o.outbound_date BETWEEN ? AND ? " 
				+ "GROUP BY p.partner_id, p.name " 
				+ "ORDER BY total_outbound DESC "
				+ "LIMIT 5";
		
		List<ClientOutboundRanking> result = new ArrayList<>();
		
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setObject(1, from);
			pstmt.setObject(2, to);
			
			try (ResultSet rs = pstmt.executeQuery()) {
				int rank = 1;

				while (rs.next()) {
					long partnerId = rs.getLong("partner_id");
					String name = rs.getString("name");
					int totalOutbound = rs.getInt("total_outbound");

					ClientOutboundRanking ranking = new ClientOutboundRanking(rank++, partnerId, name, totalOutbound);

					result.add(ranking);
				}
			}
		} 
		return result;
	}
	
	// 거래처 월별 거래 추이 조회
	public List<ClientMonthlyTrend> selectMonthlyTrend (Connection conn, String start, String end) throws SQLException {
		String sql = "SELECT p.partner_id, p.name, DATE_FORMAT(o.outbound_date, '%Y-%m') AS month_period, "
				+ "COALESCE(SUM(o.quantity), 0) AS total_qty, "
				+ "COUNT(o.outbound_id) AS transaction_count "
				+ "FROM outbound o "
				+ "JOIN partner p ON o.partner_id = p.partner_id "
				+ "WHERE o.outbound_date >= STR_TO_DATE(CONCAT(?, '-01'), '%Y-%m-%d') "
				+ "AND o.outbound_date < DATE_ADD(STR_TO_DATE(CONCAT(?, '-01'), '%Y-%m-%d'), INTERVAL 1 MONTH) "
				+ "GROUP BY p.partner_id, p.name, "
				+ "DATE_FORMAT(o.outbound_date, '%Y-%m') "
				+ "ORDER BY total_qty ASC, month_period ASC";
		
		List<ClientMonthlyTrend> result = new ArrayList<>();
		
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, start);
			pstmt.setString(2, end);
			
			try (ResultSet rs = pstmt.executeQuery()){
				while(rs.next()) {
					ClientMonthlyTrend trend = new ClientMonthlyTrend();
					trend.setPartnerId(rs.getLong("partner_id"));
					trend.setPartnerName(rs.getString("name"));
					trend.setYearMonth(rs.getString("month_period"));
					trend.setMonthlyQty(rs.getInt("total_qty"));
					trend.setTransactionCount(rs.getInt("transaction_count"));
					
					result.add(trend);
				}
			} 
		} 
		return result;
	}
	
	// 창고별 집계
	public Map<String, Integer> selectWarehouseStock(Connection conn) throws SQLException {
		String sql = "SELECT w.name, "
				+ "COALESCE(SUM(s.quantity), 0) AS stock_qty "
				+ "FROM stock_lot s "
				+ "JOIN zone z ON z.zone_id = s.zone_id "
				+ "JOIN warehouse w ON w.warehouse_id = z.warehouse_id "
				+ "GROUP BY w.warehouse_id, w.name "
				+ "ORDER BY w.warehouse_id";
		
		Map<String, Integer> result = new LinkedHashMap<>();
		
		try (PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()){
			while(rs.next()) {
				result.put(rs.getString("name"), rs.getInt("stock_qty"));
			}
		}
		return result;
	}
}
