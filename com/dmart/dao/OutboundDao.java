package com.dmart.dao;

import com.dmart.dto.Outbound;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OutboundDao {

    public Long insert(Connection conn, Outbound outbound) throws SQLException {
        String sql = "INSERT INTO OUTBOUND (lot_id, partner_id, quantity, outbound_date, created_by) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, outbound.getLotId());
            ps.setLong(2, outbound.getPartnerId());
            ps.setInt(3, outbound.getQuantity());
            ps.setDate(4, Date.valueOf(outbound.getOutboundDate()));
            ps.setLong(5, outbound.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, Outbound outbound) throws SQLException {
        String sql = "UPDATE OUTBOUND SET lot_id = ?, partner_id = ?, quantity = ?, outbound_date = ?, created_by = ? WHERE outbound_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, outbound.getLotId());
            ps.setLong(2, outbound.getPartnerId());
            ps.setInt(3, outbound.getQuantity());
            ps.setDate(4, Date.valueOf(outbound.getOutboundDate()));
            ps.setLong(5, outbound.getCreatedBy());
            ps.setLong(6, outbound.getOutboundId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long outboundId) throws SQLException {
        String sql = "DELETE FROM OUTBOUND WHERE outbound_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, outboundId);
            return ps.executeUpdate() > 0;
        }
    }

    // 재고 로트 삭제(10.3) 안전장치용: 이 로트가 한 번이라도 출고된 적이 있는지.
    public boolean existsByLotId(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT 1 FROM OUTBOUND WHERE lot_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Outbound findById(Connection conn, Long outboundId) throws SQLException {
        String sql = "SELECT * FROM OUTBOUND WHERE outbound_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, outboundId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Outbound> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM OUTBOUND ORDER BY outbound_id";
        List<Outbound> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 출고 이력 화면용. OUTBOUND엔 item_id/item_name이 없어서 STOCK_LOT을 조인해 거르고,
    // keyword(품목명 검색)가 있을 때만 ITEM까지 추가로 조인한다.
    public List<Outbound> findPage(Connection conn, Long itemId, String keyword, int offset, int limit) throws SQLException {
        String sql = "SELECT o.* FROM OUTBOUND o JOIN STOCK_LOT sl ON o.lot_id = sl.lot_id"
                + joinClause(keyword) + whereClause(itemId, keyword)
                + " ORDER BY o.outbound_id DESC LIMIT ? OFFSET ?";
        List<Outbound> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, itemId, keyword);
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

    public int count(Connection conn, Long itemId, String keyword) throws SQLException {
        String sql = "SELECT COUNT(*) FROM OUTBOUND o JOIN STOCK_LOT sl ON o.lot_id = sl.lot_id"
                + joinClause(keyword) + whereClause(itemId, keyword);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, itemId, keyword);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String joinClause(String keyword) {
        return keyword != null ? " JOIN ITEM i ON sl.item_id = i.item_id" : "";
    }

    private String whereClause(Long itemId, String keyword) {
        StringBuilder sb = new StringBuilder();
        if (itemId != null) {
            sb.append(" AND sl.item_id = ?");
        }
        if (keyword != null) {
            sb.append(" AND i.item_name LIKE ?");
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, Long itemId, String keyword) throws SQLException {
        int idx = startIndex;
        if (itemId != null) {
            ps.setLong(idx++, itemId);
        }
        if (keyword != null) {
            ps.setString(idx++, "%" + keyword + "%");
        }
        return idx;
    }

    private Outbound mapRow(ResultSet rs) throws SQLException {
        Outbound outbound = new Outbound();
        outbound.setOutboundId(rs.getLong("outbound_id"));
        outbound.setLotId(rs.getLong("lot_id"));
        outbound.setPartnerId(rs.getLong("partner_id"));
        outbound.setQuantity(rs.getInt("quantity"));
        outbound.setOutboundDate(rs.getDate("outbound_date").toLocalDate());
        outbound.setCreatedBy(rs.getLong("created_by"));
        return outbound;
    }
}
