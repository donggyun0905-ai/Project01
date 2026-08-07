package com.bottommart.dao;

import com.bottommart.db.DBConnection;
import com.bottommart.model.ReturnDisposal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReturnDisposalDao {

    public Long insert(ReturnDisposal record) throws SQLException {
        String sql = "INSERT INTO RETURN_DISPOSAL (lot_id, type, reason, quantity, processed_by, processed_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, record.getLotId());
            ps.setString(2, record.getType());
            ps.setString(3, record.getReason());
            ps.setInt(4, record.getQuantity());
            ps.setLong(5, record.getProcessedBy());
            ps.setDate(6, Date.valueOf(record.getProcessedDate()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(ReturnDisposal record) throws SQLException {
        String sql = "UPDATE RETURN_DISPOSAL SET lot_id = ?, type = ?, reason = ?, quantity = ?, "
                + "processed_by = ?, processed_date = ? WHERE record_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, record.getLotId());
            ps.setString(2, record.getType());
            ps.setString(3, record.getReason());
            ps.setInt(4, record.getQuantity());
            ps.setLong(5, record.getProcessedBy());
            ps.setDate(6, Date.valueOf(record.getProcessedDate()));
            ps.setLong(7, record.getRecordId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Long recordId) throws SQLException {
        String sql = "DELETE FROM RETURN_DISPOSAL WHERE record_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            return ps.executeUpdate() > 0;
        }
    }

    public ReturnDisposal findById(Long recordId) throws SQLException {
        String sql = "SELECT * FROM RETURN_DISPOSAL WHERE record_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<ReturnDisposal> findAll() throws SQLException {
        String sql = "SELECT * FROM RETURN_DISPOSAL ORDER BY record_id";
        List<ReturnDisposal> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private ReturnDisposal mapRow(ResultSet rs) throws SQLException {
        ReturnDisposal record = new ReturnDisposal();
        record.setRecordId(rs.getLong("record_id"));
        record.setLotId(rs.getLong("lot_id"));
        record.setType(rs.getString("type"));
        record.setReason(rs.getString("reason"));
        record.setQuantity(rs.getInt("quantity"));
        record.setProcessedBy(rs.getLong("processed_by"));
        record.setProcessedDate(rs.getDate("processed_date").toLocalDate());
        return record;
    }
}
