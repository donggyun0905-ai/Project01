package com.bottommart.dao;

import com.bottommart.db.DBConnection;
import com.bottommart.model.Alert;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AlertDao {

    public Long insert(Alert alert) throws SQLException {
        String sql = "INSERT INTO ALERT (item_id, alert_type, message, is_resolved) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    public boolean update(Alert alert) throws SQLException {
        String sql = "UPDATE ALERT SET item_id = ?, alert_type = ?, message = ?, is_resolved = ? WHERE alert_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, alert.getItemId());
            ps.setString(2, alert.getAlertType());
            ps.setString(3, alert.getMessage());
            ps.setBoolean(4, alert.getIsResolved() != null ? alert.getIsResolved() : false);
            ps.setLong(5, alert.getAlertId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Long alertId) throws SQLException {
        String sql = "DELETE FROM ALERT WHERE alert_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, alertId);
            return ps.executeUpdate() > 0;
        }
    }

    public Alert findById(Long alertId) throws SQLException {
        String sql = "SELECT * FROM ALERT WHERE alert_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, alertId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Alert> findUnresolved() throws SQLException {
        String sql = "SELECT * FROM ALERT WHERE is_resolved = FALSE ORDER BY created_at DESC";
        List<Alert> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    public List<Alert> findAll() throws SQLException {
        String sql = "SELECT * FROM ALERT ORDER BY alert_id";
        List<Alert> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
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
