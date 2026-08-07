package com.bottommart.dao;

import com.bottommart.dto.AppUser;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Connection을 직접 열지 않고 호출자에게서 받는다.
// 여러 DAO 호출을 하나의 트랜잭션으로 묶어야 할 때(DBConnection.executeInTransaction) 필요하기 때문.
public class AppUserDao {

    public Long insert(Connection conn, AppUser user) throws SQLException {
        String sql = "INSERT INTO APP_USER (username, password, name, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getRole());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, AppUser user) throws SQLException {
        String sql = "UPDATE APP_USER SET username = ?, password = ?, name = ?, role = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getRole());
            ps.setLong(5, user.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long userId) throws SQLException {
        String sql = "DELETE FROM APP_USER WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public AppUser findById(Connection conn, Long userId) throws SQLException {
        String sql = "SELECT * FROM APP_USER WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<AppUser> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM APP_USER ORDER BY user_id";
        List<AppUser> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private AppUser mapRow(ResultSet rs) throws SQLException {
        AppUser user = new AppUser();
        user.setUserId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setName(rs.getString("name"));
        user.setRole(rs.getString("role"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        return user;
    }
}
