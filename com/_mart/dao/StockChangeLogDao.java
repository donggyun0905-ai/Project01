package com._mart.dao;

import com._mart.dto.StockChangeLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StockChangeLogDao {

    public Long insert(Connection conn, StockChangeLog log) throws SQLException {
        String sql = "INSERT INTO STOCK_CHANGE_LOG (lot_id, changed_by, change_type, before_value, "
                + "after_value, reason, is_reverted) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, log.getLotId());
            ps.setLong(2, log.getChangedBy());
            ps.setString(3, log.getChangeType());
            ps.setString(4, log.getBeforeValue());
            ps.setString(5, log.getAfterValue());
            ps.setString(6, log.getReason());
            ps.setBoolean(7, log.getIsReverted() != null ? log.getIsReverted() : false);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, StockChangeLog log) throws SQLException {
        String sql = "UPDATE STOCK_CHANGE_LOG SET lot_id = ?, changed_by = ?, change_type = ?, "
                + "before_value = ?, after_value = ?, reason = ?, is_reverted = ? WHERE log_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, log.getLotId());
            ps.setLong(2, log.getChangedBy());
            ps.setString(3, log.getChangeType());
            ps.setString(4, log.getBeforeValue());
            ps.setString(5, log.getAfterValue());
            ps.setString(6, log.getReason());
            ps.setBoolean(7, log.getIsReverted() != null ? log.getIsReverted() : false);
            ps.setLong(8, log.getLogId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long logId) throws SQLException {
        String sql = "DELETE FROM STOCK_CHANGE_LOG WHERE log_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, logId);
            return ps.executeUpdate() > 0;
        }
    }

    public StockChangeLog findById(Connection conn, Long logId) throws SQLException {
        String sql = "SELECT * FROM STOCK_CHANGE_LOG WHERE log_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<StockChangeLog> findByLotId(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT * FROM STOCK_CHANGE_LOG WHERE lot_id = ? ORDER BY changed_at DESC";
        List<StockChangeLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public List<StockChangeLog> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM STOCK_CHANGE_LOG ORDER BY log_id";
        List<StockChangeLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private StockChangeLog mapRow(ResultSet rs) throws SQLException {
        StockChangeLog log = new StockChangeLog();
        log.setLogId(rs.getLong("log_id"));
        log.setLotId(rs.getLong("lot_id"));
        log.setChangedBy(rs.getLong("changed_by"));
        log.setChangeType(rs.getString("change_type"));
        log.setBeforeValue(rs.getString("before_value"));
        log.setAfterValue(rs.getString("after_value"));
        log.setReason(rs.getString("reason"));
        log.setIsReverted(rs.getBoolean("is_reverted"));
        Timestamp changedAt = rs.getTimestamp("changed_at");
        if (changedAt != null) {
            log.setChangedAt(changedAt.toLocalDateTime());
        }
        return log;
    }
}
