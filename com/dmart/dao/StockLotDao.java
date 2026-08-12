package com.dmart.dao;

import com.dmart.dto.StockLot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StockLotDao {

    public Long insert(Connection conn, StockLot lot) throws SQLException {
        String sql = "INSERT INTO STOCK_LOT (item_id, zone_id, partner_id, quantity, inbound_date, "
                + "expiry_date, status, created_by, parent_lot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, lot.getItemId());
            ps.setLong(2, lot.getZoneId());
            ps.setLong(3, lot.getPartnerId());
            ps.setInt(4, lot.getQuantity());
            ps.setDate(5, Date.valueOf(lot.getInboundDate()));
            ps.setDate(6, lot.getExpiryDate() != null ? Date.valueOf(lot.getExpiryDate()) : null);
            ps.setString(7, lot.getStatus() != null ? lot.getStatus() : "NORMAL");
            ps.setLong(8, lot.getCreatedBy());
            if (lot.getParentLotId() != null) {
                ps.setLong(9, lot.getParentLotId());
            } else {
                ps.setNull(9, Types.BIGINT);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, StockLot lot) throws SQLException {
        String sql = "UPDATE STOCK_LOT SET item_id = ?, zone_id = ?, partner_id = ?, quantity = ?, "
                + "inbound_date = ?, expiry_date = ?, status = ?, created_by = ?, parent_lot_id = ? WHERE lot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lot.getItemId());
            ps.setLong(2, lot.getZoneId());
            ps.setLong(3, lot.getPartnerId());
            ps.setInt(4, lot.getQuantity());
            ps.setDate(5, Date.valueOf(lot.getInboundDate()));
            ps.setDate(6, lot.getExpiryDate() != null ? Date.valueOf(lot.getExpiryDate()) : null);
            ps.setString(7, lot.getStatus() != null ? lot.getStatus() : "NORMAL");
            ps.setLong(8, lot.getCreatedBy());
            if (lot.getParentLotId() != null) {
                ps.setLong(9, lot.getParentLotId());
            } else {
                ps.setNull(9, Types.BIGINT);
            }
            ps.setLong(10, lot.getLotId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long lotId) throws SQLException {
        String sql = "DELETE FROM STOCK_LOT WHERE lot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            return ps.executeUpdate() > 0;
        }
    }

    // quantity 차감(출고/이동/반품폐기) 도중 동시 요청이 끼어드는 걸 막기 위해
    // 트랜잭션 안에서 이 row를 잠그고 조회한다. OutboundService 등에서 사용.
    public StockLot findByIdForUpdate(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE lot_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public StockLot findById(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE lot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // FIFO 출고 순서 추천: 입고일이 빠른 순
    public List<StockLot> findByItemIdOrderByInboundDate(Connection conn, Long itemId) throws SQLException {
        return findByItemIdOrdered(conn, itemId, "inbound_date");
    }

    // FEFO 출고 순서 추천: 유통기한이 이른 순
    public List<StockLot> findByItemIdOrderByExpiryDate(Connection conn, Long itemId) throws SQLException {
        return findByItemIdOrdered(conn, itemId, "expiry_date");
    }

    private List<StockLot> findByItemIdOrdered(Connection conn, Long itemId, String orderColumn) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE item_id = ? ORDER BY " + orderColumn + " ASC";
        List<StockLot> result = new ArrayList<>();
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

    // 구역 용량 체크용: 그 구역에 현재 들어있는 활성(NORMAL) 재고 합계
    public int sumQuantityByZoneId(Connection conn, Long zoneId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM STOCK_LOT WHERE zone_id = ? AND status = 'NORMAL'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // 품목 capacity_max 체크용: 그 품목의 전체 창고에 걸친 활성(NORMAL) 재고 합계
    public int sumQuantityByItemId(Connection conn, Long itemId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM STOCK_LOT WHERE item_id = ? AND status = 'NORMAL'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // 승인 자동실행(발주)에서 zoneId/partnerId 기본값을 정할 때 사용 — 12번 참고.
    // "가장 최근 이 품목이 입고됐던 곳과 같은 곳에 다시 입고한다"는 가정.
    public StockLot findMostRecentNormalByItemId(Connection conn, Long itemId) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE item_id = ? AND status = 'NORMAL' ORDER BY inbound_date DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<StockLot> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT ORDER BY lot_id";
        List<StockLot> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private StockLot mapRow(ResultSet rs) throws SQLException {
        StockLot lot = new StockLot();
        lot.setLotId(rs.getLong("lot_id"));
        lot.setItemId(rs.getLong("item_id"));
        lot.setZoneId(rs.getLong("zone_id"));
        lot.setPartnerId(rs.getLong("partner_id"));
        lot.setQuantity(rs.getInt("quantity"));
        lot.setInboundDate(rs.getDate("inbound_date").toLocalDate());
        Date expiry = rs.getDate("expiry_date");
        lot.setExpiryDate(expiry != null ? expiry.toLocalDate() : null);
        lot.setStatus(rs.getString("status"));
        lot.setCreatedBy(rs.getLong("created_by"));
        long parentLotId = rs.getLong("parent_lot_id");
        lot.setParentLotId(rs.wasNull() ? null : parentLotId);
        return lot;
    }
}
