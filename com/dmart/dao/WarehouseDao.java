package com.dmart.dao;

import com.dmart.dto.Warehouse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WarehouseDao {

    public Long insert(Connection conn, Warehouse warehouse) throws SQLException {
        String sql = "INSERT INTO WAREHOUSE (name, location) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, warehouse.getName());
            ps.setString(2, warehouse.getLocation());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, Warehouse warehouse) throws SQLException {
        String sql = "UPDATE WAREHOUSE SET name = ?, location = ? WHERE warehouse_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, warehouse.getName());
            ps.setString(2, warehouse.getLocation());
            ps.setLong(3, warehouse.getWarehouseId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long warehouseId) throws SQLException {
        String sql = "DELETE FROM WAREHOUSE WHERE warehouse_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, warehouseId);
            return ps.executeUpdate() > 0;
        }
    }

    public Warehouse findById(Connection conn, Long warehouseId) throws SQLException {
        String sql = "SELECT * FROM WAREHOUSE WHERE warehouse_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Warehouse> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM WAREHOUSE ORDER BY warehouse_id";
        List<Warehouse> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private Warehouse mapRow(ResultSet rs) throws SQLException {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseId(rs.getLong("warehouse_id"));
        warehouse.setName(rs.getString("name"));
        warehouse.setLocation(rs.getString("location"));
        return warehouse;
    }
}
