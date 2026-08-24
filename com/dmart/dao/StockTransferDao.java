package com.dmart.dao;

import com.dmart.dto.StockTransfer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StockTransferDao {

    public Long insert(Connection conn, StockTransfer transfer) throws SQLException {
        validateZones(transfer);
        String sql = "INSERT INTO STOCK_TRANSFER (lot_id, from_zone_id, to_zone_id, quantity, handler_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    public boolean update(Connection conn, StockTransfer transfer) throws SQLException {
        validateZones(transfer);
        String sql = "UPDATE STOCK_TRANSFER SET lot_id = ?, from_zone_id = ?, to_zone_id = ?, quantity = ?, handler_id = ? WHERE transfer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, transfer.getLotId());
            ps.setLong(2, transfer.getFromZoneId());
            ps.setLong(3, transfer.getToZoneId());
            ps.setInt(4, transfer.getQuantity());
            ps.setLong(5, transfer.getHandlerId());
            ps.setLong(6, transfer.getTransferId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long transferId) throws SQLException {
        String sql = "DELETE FROM STOCK_TRANSFER WHERE transfer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, transferId);
            return ps.executeUpdate() > 0;
        }
    }

    public StockTransfer findById(Connection conn, Long transferId) throws SQLException {
        String sql = "SELECT * FROM STOCK_TRANSFER WHERE transfer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<StockTransfer> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM STOCK_TRANSFER ORDER BY transfer_id";
        List<StockTransfer> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 이동 이력 화면용. STOCK_TRANSFER엔 item_id가 없어서(로트 단위 이동이라) itemId로 거르려면
    // STOCK_LOT을 조인해야 함 - 최근 것부터 보여준다.
    public List<StockTransfer> findPage(Connection conn, Long itemId, int offset, int limit) throws SQLException {
        String sql = "SELECT st.* FROM STOCK_TRANSFER st JOIN STOCK_LOT sl ON st.lot_id = sl.lot_id"
                + (itemId != null ? " WHERE sl.item_id = ?" : "")
                + " ORDER BY st.transfer_id DESC LIMIT ? OFFSET ?";
        List<StockTransfer> result = new ArrayList<>();
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
        String sql = "SELECT COUNT(*) FROM STOCK_TRANSFER st JOIN STOCK_LOT sl ON st.lot_id = sl.lot_id"
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

    // from_zone과 to_zone이 같으면 안 됨 (확정안 스키마 설계 문서의 제약사항).
    // 이 검증은 원래 Service 계층에 둬야 하지만, 이 프로젝트에는 별도 Service 계층이 없어 DAO에 최소한으로 남겨둠.
    private void validateZones(StockTransfer transfer) {
        if (transfer.getFromZoneId() == null || transfer.getToZoneId() == null) {
            throw new IllegalArgumentException("fromZoneId와 toZoneId는 null일 수 없습니다.");
        }
        if (transfer.getFromZoneId().equals(transfer.getToZoneId())) {
            throw new IllegalArgumentException("fromZoneId와 toZoneId는 같을 수 없습니다.");
        }
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
