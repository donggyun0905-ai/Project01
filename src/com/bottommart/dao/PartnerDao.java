package com.bottommart.dao;

import com.bottommart.db.DBConnection;
import com.bottommart.model.Partner;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PartnerDao {

    public Long insert(Partner partner) throws SQLException {
        String sql = "INSERT INTO PARTNER (name, type, contact) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    public boolean update(Partner partner) throws SQLException {
        String sql = "UPDATE PARTNER SET name = ?, type = ?, contact = ? WHERE partner_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, partner.getName());
            ps.setString(2, partner.getType());
            ps.setString(3, partner.getContact());
            ps.setLong(4, partner.getPartnerId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Long partnerId) throws SQLException {
        String sql = "DELETE FROM PARTNER WHERE partner_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, partnerId);
            return ps.executeUpdate() > 0;
        }
    }

    public Partner findById(Long partnerId) throws SQLException {
        String sql = "SELECT * FROM PARTNER WHERE partner_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, partnerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Partner> findAll() throws SQLException {
        String sql = "SELECT * FROM PARTNER ORDER BY partner_id";
        List<Partner> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
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
