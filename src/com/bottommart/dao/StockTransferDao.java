package com.bottommart.dao;

import com.bottommart.db.DBConnection;
import com.bottommart.model.StockTransfer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StockTransferDao {

    public Long insert(StockTransfer transfer) throws SQLException {
        if (transfer.getFromZoneId().equals(transfer.getToZoneId())) {
            throw new IllegalArgumentException("fromZoneId와 toZoneId는 같을 수 없습니다.");
        }
        String sql = "INSERT INTO STOCK_TRANSFER (lot_id, from_zone_id, to_zone_id, quantity, handler_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, transfer.getLotId());
            ps.setLong(2, transfer.getFromZoneId());
            ps.setLong(3, transfer.getToZoneId());
            ps.setInt(4, transfer.getQuantity());
            ps.setLong(5, transfer.getHandlerId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(StockTransfer transfer) throws SQLException {
        if (transfer.getFromZoneId().equals(transfer.getToZoneId())) {
            throw new IllegalArgumentException("fromZoneId와 toZoneId는 같을 수 없습니다.");
        }
        String sql = "UPDATE STOCK_TRANSFER SET lot_id = ?, from_zone_id = ?, to_zone_id = ?, quantity = ?, handler_id = ? WHERE transfer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, transfer.getLotId());
            ps.setLong(2, transfer.getFromZoneId());
            ps.setLong(3, transfer.getToZoneId());
            ps.setInt(4, transfer.getQuantity());
            ps.setLong(5, transfer.getHandlerId());
            ps.setLong(6, transfer.getTransferId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Long transferId) throws SQLException {
        String sql = "DELETE FROM STOCK_TRANSFER WHERE transfer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, transferId);
            return ps.executeUpdate() > 0;
        }
    }

    public StockTransfer findById(Long transferId) throws SQLException {
        String sql = "SELECT * FROM STOCK_TRANSFER WHERE transfer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<StockTransfer> findAll() throws SQLException {
        String sql = "SELECT * FROM STOCK_TRANSFER ORDER BY transfer_id";
        List<StockTransfer> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private StockTransfer mapRow(ResultSet rs) throws SQLException {
        StockTransfer transfer = new StockTransfer();
        transfer.setTransferId(rs.getLong("transfer_id"));
        transfer.setLotId(rs.getLong("lot_id"));
        transfer.setFromZoneId(rs.getLong("from_zone_id"));
        transfer.setToZoneId(rs.getLong("to_zone_id"));
        transfer.setQuantity(rs.getInt("quantity"));
        transfer.setHandlerId(rs.getLong("handler_id"));
        Timestamp movedAt = rs.getTimestamp("moved_at");
        if (movedAt != null) {
            transfer.setMovedAt(movedAt.toLocalDateTime());
        }
        return transfer;
    }
}
