package com.dmart.dao;

import com.dmart.dto.ReturnDisposal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReturnDisposalDao {

    public Long insert(Connection conn, ReturnDisposal disposalRecord) throws SQLException {
        String sql = "INSERT INTO RETURN_DISPOSAL (lot_id, type, reason, quantity, processed_by, processed_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, disposalRecord.getLotId());
            ps.setString(2, disposalRecord.getType());
            ps.setString(3, disposalRecord.getReason());
            ps.setInt(4, disposalRecord.getQuantity());
            ps.setLong(5, disposalRecord.getProcessedBy());
            ps.setDate(6, Date.valueOf(disposalRecord.getProcessedDate()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, ReturnDisposal disposalRecord) throws SQLException {
        String sql = "UPDATE RETURN_DISPOSAL SET lot_id = ?, type = ?, reason = ?, quantity = ?, "
                + "processed_by = ?, processed_date = ? WHERE record_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, disposalRecord.getLotId());
            ps.setString(2, disposalRecord.getType());
            ps.setString(3, disposalRecord.getReason());
            ps.setInt(4, disposalRecord.getQuantity());
            ps.setLong(5, disposalRecord.getProcessedBy());
            ps.setDate(6, Date.valueOf(disposalRecord.getProcessedDate()));
            ps.setLong(7, disposalRecord.getRecordId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long recordId) throws SQLException {
        String sql = "DELETE FROM RETURN_DISPOSAL WHERE record_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            return ps.executeUpdate() > 0;
        }
    }

    public ReturnDisposal findById(Connection conn, Long recordId) throws SQLException {
        String sql = "SELECT * FROM RETURN_DISPOSAL WHERE record_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<ReturnDisposal> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM RETURN_DISPOSAL ORDER BY record_id";
        List<ReturnDisposal> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 반품/폐기 이력 화면용. RETURN_DISPOSAL엔 item_id가 없어서 STOCK_LOT을 조인해 거른다.
    public List<ReturnDisposal> findPage(Connection conn, Long itemId, int offset, int limit) throws SQLException {
        String sql = "SELECT rd.* FROM RETURN_DISPOSAL rd JOIN STOCK_LOT sl ON rd.lot_id = sl.lot_id"
                + (itemId != null ? " WHERE sl.item_id = ?" : "")
                + " ORDER BY rd.record_id DESC LIMIT ? OFFSET ?";
        List<ReturnDisposal> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            if (itemId != null) {
                ps.setLong(idx++, itemId);
            }
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

    public int count(Connection conn, Long itemId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM RETURN_DISPOSAL rd JOIN STOCK_LOT sl ON rd.lot_id = sl.lot_id"
                + (itemId != null ? " WHERE sl.item_id = ?" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (itemId != null) {
                ps.setLong(1, itemId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private ReturnDisposal mapRow(ResultSet rs) throws SQLException {
        ReturnDisposal disposalRecord = new ReturnDisposal();
        disposalRecord.setRecordId(rs.getLong("record_id"));
        disposalRecord.setLotId(rs.getLong("lot_id"));
        disposalRecord.setType(rs.getString("type"));
        disposalRecord.setReason(rs.getString("reason"));
        disposalRecord.setQuantity(rs.getInt("quantity"));
        disposalRecord.setProcessedBy(rs.getLong("processed_by"));
        disposalRecord.setProcessedDate(rs.getDate("processed_date").toLocalDate());
        return disposalRecord;
    }
}
