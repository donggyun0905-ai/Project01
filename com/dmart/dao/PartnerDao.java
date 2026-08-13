package com.dmart.dao;

import com.dmart.dto.Partner;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PartnerDao {

    public Long insert(Connection conn, Partner partner) throws SQLException {
        String sql = "INSERT INTO PARTNER (name, type, contact) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, partner.getName());
            ps.setString(2, partner.getType());
            ps.setString(3, partner.getContact());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, Partner partner) throws SQLException {
        String sql = "UPDATE PARTNER SET name = ?, type = ?, contact = ? WHERE partner_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, partner.getName());
            ps.setString(2, partner.getType());
            ps.setString(3, partner.getContact());
            ps.setLong(4, partner.getPartnerId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long partnerId) throws SQLException {
        String sql = "DELETE FROM PARTNER WHERE partner_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, partnerId);
            return ps.executeUpdate() > 0;
        }
    }

    public Partner findById(Connection conn, Long partnerId) throws SQLException {
        String sql = "SELECT * FROM PARTNER WHERE partner_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, partnerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Partner> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM PARTNER ORDER BY partner_id";
        List<Partner> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 5번 목록 API용. ITEM의 category/keyword 필터 패턴과 동일.
    public List<Partner> findPage(Connection conn, String type, String keyword, int offset, int limit) throws SQLException {
        String sql = "SELECT * FROM PARTNER" + whereClause(type, keyword) + " ORDER BY partner_id LIMIT ? OFFSET ?";
        List<Partner> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, type, keyword);
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

    public int count(Connection conn, String type, String keyword) throws SQLException {
        String sql = "SELECT COUNT(*) FROM PARTNER" + whereClause(type, keyword);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, type, keyword);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String whereClause(String type, String keyword) {
        StringBuilder sb = new StringBuilder();
        if (type != null) {
            sb.append(" AND type = ?");
        }
        if (keyword != null) {
            sb.append(" AND name LIKE ?");
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, String type, String keyword) throws SQLException {
        int idx = startIndex;
        if (type != null) {
            ps.setString(idx++, type);
        }
        if (keyword != null) {
            ps.setString(idx++, "%" + keyword + "%");
        }
        return idx;
    }

    private Partner mapRow(ResultSet rs) throws SQLException {
        Partner partner = new Partner();
        partner.setPartnerId(rs.getLong("partner_id"));
        partner.setName(rs.getString("name"));
        partner.setType(rs.getString("type"));
        partner.setContact(rs.getString("contact"));
        return partner;
    }
}
