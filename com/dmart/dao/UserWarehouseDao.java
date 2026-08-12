package com.dmart.dao;

import com.dmart.dto.UserWarehouse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// 복합키(user_id, warehouse_id) 테이블이라 update는 의미가 없어 insert/delete/find만 제공
public class UserWarehouseDao {

    public void insert(Connection conn, UserWarehouse assignment) throws SQLException {
        String sql = "INSERT INTO USER_WAREHOUSE (user_id, warehouse_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, assignment.getUserId());
            ps.setLong(2, assignment.getWarehouseId());
            ps.executeUpdate();
        }
    }

    public boolean deleteById(Connection conn, Long userId, Long warehouseId) throws SQLException {
        String sql = "DELETE FROM USER_WAREHOUSE WHERE user_id = ? AND warehouse_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, warehouseId);
            return ps.executeUpdate() > 0;
        }
    }

    public UserWarehouse findById(Connection conn, Long userId, Long warehouseId) throws SQLException {
        String sql = "SELECT * FROM USER_WAREHOUSE WHERE user_id = ? AND warehouse_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<UserWarehouse> findByUserId(Connection conn, Long userId) throws SQLException {
        String sql = "SELECT * FROM USER_WAREHOUSE WHERE user_id = ?";
        List<UserWarehouse> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public List<UserWarehouse> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM USER_WAREHOUSE ORDER BY user_id, warehouse_id";
        List<UserWarehouse> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private UserWarehouse mapRow(ResultSet rs) throws SQLException {
        return new UserWarehouse(rs.getLong("user_id"), rs.getLong("warehouse_id"));
    }
}
