package com.dmart.report.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.dmart.report.dto.DailyComparison;
import com.dmart.report.dto.LowStockItem;
import com.dmart.report.dto.TopOutboundItem;

public class DailyReportDao {

	// 전일 대비 입출고 증감량 / 증감률
	public DailyComparison selectDailyComparison(Connection conn, LocalDate date) throws SQLException{
		String sql = "SELECT COALESCE(SUM(CASE WHEN inbound_date = ? THEN quantity ELSE 0 END), 0) AS today_inbound, "
				+ "COALESCE(SUM(CASE WHEN inbound_date = DATE_SUB(?, INTERVAL 1 DAY) THEN quantity ELSE 0 END), 0) AS yesterday_inbound, "
				+ "( SELECT COALESCE(SUM(quantity), 0) FROM outbound WHERE outbound_date = ? ) AS today_outbound, "
				+ "( SELECT COALESCE(SUM(quantity), 0) FROM outbound WHERE outbound_date = DATE_SUB(?, INTERVAL 1 DAY)) AS yesterday_outbound "
				+ "FROM stock_lot " 
				+ "WHERE inbound_date IN (?, DATE_SUB(?, INTERVAL 1 DAY))";

		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setObject(1, date);
			pstmt.setObject(2, date);
			pstmt.setObject(3, date);
			pstmt.setObject(4, date);
			pstmt.setObject(5, date);
			pstmt.setObject(6, date);

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					int todayInbound = rs.getInt("today_inbound");
					int yesterdayInbound = rs.getInt("yesterday_inbound");
					int todayOutbound = rs.getInt("today_outbound");
					int yesterdayOutbound = rs.getInt("yesterday_outbound");

					return new DailyComparison(todayInbound, yesterdayInbound, todayOutbound, yesterdayOutbound);
				}
			}
		} 
		return new DailyComparison(0, 0, 0, 0);
	}

	// 재고 부족 품목 리스트 쿼리문 작성 필요
	public List<LowStockItem> selectLowStockItems(Connection conn, String alertType) throws SQLException{
		String sql = "SELECT i.item_id, i.item_name, i.threshold_min, " 
				+ "COALESCE(s_sum.current_qty, 0) AS current_qty "
				+ "FROM item i " 
				+ "LEFT JOIN (SELECT item_id, SUM(quantity) AS current_qty " 
				+ "FROM stock_lot "
				+ "WHERE status = 'NORMAL' " 
				+ "GROUP BY item_id " 
				+ ") s_sum ON s_sum.item_id = i.item_id "
				+ "WHERE EXISTS (SELECT 1 FROM alert a " 
				+ "WHERE a.item_id = i.item_id " 
				+ "AND a.is_resolved = FALSE "
				+ "AND a.alert_type = ? " 
				+ ") AND COALESCE(s_sum.current_qty, 0) < i.threshold_min "
				+ "ORDER BY current_qty ASC";

		List<LowStockItem> result = new ArrayList<>();

		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, alertType);

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(new LowStockItem(rs.getLong("item_id"), 
							rs.getString("item_name"),
							rs.getInt("current_qty"), 
							rs.getInt("threshold_min")));
				}
			}
		}
		return result;
	}

	// 금일 출고량 Top5 리스트
	public List<TopOutboundItem> selectTop5OutboundItems(Connection conn, LocalDate date) throws SQLException{
		String sql = "SELECT l.item_id, i.item_name, SUM(o.quantity) AS total_outbound " 
				+ "FROM outbound o "
				+ "JOIN stock_lot l ON o.lot_id = l.lot_id " 
				+ "JOIN item i ON l.item_id = i.item_id "
				+ "WHERE o.outbound_date = ? " 
				+ "GROUP BY l.item_id, i.item_name " 
				+ "ORDER BY total_outbound DESC "
				+ "LIMIT 5";

		List<TopOutboundItem> result = new ArrayList<>();

		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setObject(1, date);

			try (ResultSet rs = pstmt.executeQuery()) {
				int rank = 1;

				while (rs.next()) {
					String itemName = rs.getString("item_name");
					int totalOutbound = rs.getInt("total_outbound");

					TopOutboundItem ranking = new TopOutboundItem(rank++, itemName, totalOutbound);

					result.add(ranking);
				}
			}
		}
		return result;
	}
}
