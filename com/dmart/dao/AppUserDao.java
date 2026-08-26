package com.dmart.dao;

import com.dmart.dto.AppUser;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Connection을 직접 열지 않고 호출자에게서 받는다.
// 여러 DAO 호출을 하나의 트랜잭션으로 묶어야 할 때(DBConnection.executeInTransaction) 필요하기 때문.
public class AppUserDao {

    public Long insert(Connection conn, AppUser user) throws SQLException {
        String sql = "INSERT INTO APP_USER (username, password, name, role, is_active) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getRole());
            ps.setBoolean(5, user.getIsActive() != null ? user.getIsActive() : true);
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
        String sql = "UPDATE APP_USER SET username = ?, password = ?, name = ?, role = ?, is_active = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getRole());
            ps.setBoolean(5, user.getIsActive() != null ? user.getIsActive() : true);
            ps.setLong(6, user.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean setActive(Connection conn, Long userId, boolean isActive) throws SQLException {
        String sql = "UPDATE APP_USER SET is_active = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isActive);
            ps.setLong(2, userId);
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

    public AppUser findByUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT * FROM APP_USER WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
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

    // 2.3 목록 API용. role/active/usernameKeyword/nameKeyword 다 선택 필터.
    public List<AppUser> findPage(Connection conn, String role, Boolean active, String usernameKeyword,
                                   String nameKeyword, int offset, int limit) throws SQLException {
        String sql = "SELECT * FROM APP_USER" + whereClause(role, active, usernameKeyword, nameKeyword)
                + " ORDER BY user_id LIMIT ? OFFSET ?";
        List<AppUser> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, role, active, usernameKeyword, nameKeyword);
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

    public int count(Connection conn, String role, Boolean active, String usernameKeyword, String nameKeyword) throws SQLException {
        String sql = "SELECT COUNT(*) FROM APP_USER" + whereClause(role, active, usernameKeyword, nameKeyword);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, role, active, usernameKeyword, nameKeyword);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String whereClause(String role, Boolean active, String usernameKeyword, String nameKeyword) {
        StringBuilder sb = new StringBuilder();
        if (role != null) {
            sb.append(" AND role = ?");
        }
        if (active != null) {
            sb.append(" AND is_active = ?");
        }
        if (usernameKeyword != null) {
            sb.append(" AND username LIKE ?");
        }
        if (nameKeyword != null) {
            sb.append(" AND name LIKE ?");
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, String role, Boolean active,
                                  String usernameKeyword, String nameKeyword) throws SQLException {
        int idx = startIndex;
        if (role != null) {
            ps.setString(idx++, role);
        }
        if (active != null) {
            ps.setBoolean(idx++, active);
        }
        if (usernameKeyword != null) {
            ps.setString(idx++, "%" + usernameKeyword + "%");
        }
        if (nameKeyword != null) {
            ps.setString(idx++, "%" + nameKeyword + "%");
        }
        return idx;
    }

    private AppUser mapRow(ResultSet rs) throws SQLException {
        AppUser user = new AppUser();
        user.setUserId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setName(rs.getString("name"));
        user.setRole(rs.getString("role"));
        user.setIsActive(rs.getBoolean("is_active"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        return user;
    }
}
