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
