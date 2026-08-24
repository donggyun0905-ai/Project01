package com.dmart.report.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.dmart.report.dto.AlertHistory;
import com.dmart.report.dto.InOutLog;

/*
 * 통계 보고서 -> 기존에 있는 StatisticsDao 및 Service 사용
 */

public class ExportDao {
	
	// 입출고 로그
	public List<InOutLog> selectInOutboundLog(Connection conn, LocalDate from, LocalDate to) throws SQLException {
				// 입고 이력
		String sql = "SELECT s.lot_id, s.item_id, i.item_name, " 
				+ "'INBOUND' AS log_type, "
				+ "s.initial_quantity AS quantity, "
				+ "p.name AS partner_name, "
				+ "u.name AS op_name, "
				+ "s.inbound_date AS processed_at "
				+ "FROM stock_lot s "
				+ "JOIN item i ON s.item_id = i.item_id "
				+ "JOIN partner p ON s.partner_id = p.partner_id "
				+ "JOIN app_user u ON s.created_by = u.user_id "
				+ "WHERE s.inbound_date BETWEEN ? AND ? "
				+ "UNION ALL "
				
				// 출고 이력
				+ "SELECT o.lot_id, s.item_id, i.item_name, "
				+ "'OUTBOUND' AS log_type, o.quantity, "
				+ "p.name AS partner_name, "
				+ "u.name AS op_name, "
				+ "o.outbound_date AS processed_at "
				+ "FROM outbound o "
				+ "JOIN stock_lot s ON o.lot_id = s.lot_id "
				+ "JOIN item i ON s.item_id = i.item_id "
				+ "JOIN partner p ON o.partner_id = p.partner_id "
				+ "JOIN app_user u ON o.created_by = u.user_id "
				+ "WHERE o.outbound_date BETWEEN ? AND ? "
				+ "ORDER BY processed_at DESC ";
		
		List<InOutLog> result = new ArrayList<>();
		
		try(PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setFetchSize(1000);
			
			// 입고 조회 기간
			pstmt.setDate(1, java.sql.Date.valueOf(from));
			pstmt.setDate(2, java.sql.Date.valueOf(to));
			
			// 출고 조회 기간
			pstmt.setDate(3, java.sql.Date.valueOf(from));
			pstmt.setDate(4, java.sql.Date.valueOf(to));

			try(ResultSet rs = pstmt.executeQuery()){
				while(rs.next()) {
					InOutLog inout = new InOutLog();
					inout.setLotId(rs.getLong("lot_id"));
					inout.setItemId(rs.getLong("item_id"));
					inout.setItemName(rs.getString("item_name"));
					inout.setType(rs.getString("log_type"));
					inout.setQuantity(rs.getInt("quantity"));
					inout.setPartnerName(rs.getString("partner_name"));
					inout.setOpName(rs.getString("op_name"));
					
					java.sql.Date processedDate = rs.getDate("processed_at");
					inout.setProcessedAt(processedDate != null ? processedDate.toLocalDate() : null);
					
					result.add(inout);
				}
			} 
		}
		return result;
	}
	
	// 알림 이력
	public List<AlertHistory> selectAlertHistory (Connection conn, LocalDate from, LocalDate to) throws SQLException {
		String sql = "SELECT a.alert_id, a.item_id, i.item_name, a.alert_type, a.message, a.is_resolved, a.created_at "
				+ "FROM alert a "
				+ "JOIN item i ON a.item_id = i.item_id "
				+ "WHERE a.created_at >= ? "
				+ "AND a.created_at < DATE_ADD(?, INTERVAL 1 DAY) "
				+ "ORDER BY a.created_at DESC";
		
		List<AlertHistory> result = new ArrayList<>();
		
		try(PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setDate(1, java.sql.Date.valueOf(from));
			pstmt.setDate(2, java.sql.Date.valueOf(to));
			
			try (ResultSet rs = pstmt.executeQuery()) {
				while(rs.next()) {
					AlertHistory alert = new AlertHistory();
					
					alert.setAlertId(rs.getLong("alert_id"));
					alert.setItemId(rs.getLong("item_id"));
					alert.setItemName(rs.getString("item_name"));
					alert.setAlertType(rs.getString("alert_type"));
					alert.setMessage(rs.getString("message"));
					alert.setIsResolved(rs.getBoolean("is_resolved"));
					
					Timestamp createdAt = rs.getTimestamp("created_at");
	                if (createdAt != null) alert.setCreatedAt(createdAt.toLocalDateTime());
					
	                result.add(alert);
				}
			}
		}
		return result;
	}
}
