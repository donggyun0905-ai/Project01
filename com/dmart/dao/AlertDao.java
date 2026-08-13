package com.dmart.dao;

import com.dmart.dto.Alert;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AlertDao {

    public Long insert(Connection conn, Alert alert) throws SQLException {
        String sql = "INSERT INTO ALERT (item_id, alert_type, message, is_resolved) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, alert.getItemId());
            ps.setString(2, alert.getAlertType());
            ps.setString(3, alert.getMessage());
            ps.setBoolean(4, alert.getIsResolved() != null ? alert.getIsResolved() : false);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, Alert alert) throws SQLException {
        String sql = "UPDATE ALERT SET item_id = ?, alert_type = ?, message = ?, is_resolved = ? WHERE alert_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, alert.getItemId());
            ps.setString(2, alert.getAlertType());
            ps.setString(3, alert.getMessage());
            ps.setBoolean(4, alert.getIsResolved() != null ? alert.getIsResolved() : false);
            ps.setLong(5, alert.getAlertId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long alertId) throws SQLException {
        String sql = "DELETE FROM ALERT WHERE alert_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, alertId);
            return ps.executeUpdate() > 0;
        }
    }

    public Alert findById(Connection conn, Long alertId) throws SQLException {
        String sql = "SELECT * FROM ALERT WHERE alert_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, alertId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Alert> findByItemId(Connection conn, Long itemId) throws SQLException {
        String sql = "SELECT * FROM ALERT WHERE item_id = ? ORDER BY created_at DESC";
        List<Alert> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public List<Alert> findUnresolved(Connection conn) throws SQLException {
        String sql = "SELECT * FROM ALERT WHERE is_resolved = FALSE ORDER BY created_at DESC";
        List<Alert> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    public List<Alert> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM ALERT ORDER BY alert_id";
        List<Alert> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 11번 목록 API용. resolved/itemId 둘 다 선택 필터.
    public List<Alert> findPage(Connection conn, Boolean resolved, Long itemId, int offset, int limit) throws SQLException {
        String sql = "SELECT * FROM ALERT" + whereClause(resolved, itemId) + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Alert> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, resolved, itemId);
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public int count(Connection conn, Boolean resolved, Long itemId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ALERT" + whereClause(resolved, itemId);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, resolved, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String whereClause(Boolean resolved, Long itemId) {
        StringBuilder sb = new StringBuilder();
        if (resolved != null) {
            sb.append(" AND is_resolved = ?");
        }
        if (itemId != null) {
            sb.append(" AND item_id = ?");
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, Boolean resolved, Long itemId) throws SQLException {
        int idx = startIndex;
        if (resolved != null) {
            ps.setBoolean(idx++, resolved);
        }
        if (itemId != null) {
            ps.setLong(idx++, itemId);
        }
        return idx;
    }

    // 11번 "자동 해결 규칙" 참고. AlertResolutionService에서 재고가 정상 범위로 돌아왔을 때 호출.
    public int resolveUnresolvedByItemIdAndType(Connection conn, Long itemId, String alertType) throws SQLException {
        String sql = "UPDATE ALERT SET is_resolved = TRUE WHERE item_id = ? AND alert_type = ? AND is_resolved = FALSE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            ps.setString(2, alertType);
            return ps.executeUpdate();
        }
    }

    private Alert mapRow(ResultSet rs) throws SQLException {
        Alert alert = new Alert();
        alert.setAlertId(rs.getLong("alert_id"));
        alert.setItemId(rs.getLong("item_id"));
        alert.setAlertType(rs.getString("alert_type"));
        alert.setMessage(rs.getString("message"));
        alert.setIsResolved(rs.getBoolean("is_resolved"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            alert.setCreatedAt(createdAt.toLocalDateTime());
        }
        return alert;
    }
}
