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

    // 4번 목록 API용. allowedIds가 null이면 전체(ADMIN), 비어있지 않은 리스트면 그 창고들만(STAFF).
    // 빈 리스트(배정된 창고가 하나도 없는 STAFF)는 쿼리 없이 바로 빈 결과를 반환한다.
    public List<Warehouse> findPage(Connection conn, List<Long> allowedIds, int offset, int limit) throws SQLException {
        if (allowedIds != null && allowedIds.isEmpty()) {
            return new ArrayList<>();
        }
        String sql = "SELECT * FROM WAREHOUSE" + whereClause(allowedIds) + " ORDER BY warehouse_id LIMIT ? OFFSET ?";
        List<Warehouse> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindIds(ps, 1, allowedIds);
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

    public int count(Connection conn, List<Long> allowedIds) throws SQLException {
        if (allowedIds != null && allowedIds.isEmpty()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM WAREHOUSE" + whereClause(allowedIds);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindIds(ps, 1, allowedIds);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String whereClause(List<Long> allowedIds) {
        if (allowedIds == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" WHERE warehouse_id IN (");
        for (int i = 0; i < allowedIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.append(')').toString();
    }

    private int bindIds(PreparedStatement ps, int startIndex, List<Long> allowedIds) throws SQLException {
        if (allowedIds == null) {
            return startIndex;
        }
        int idx = startIndex;
        for (Long id : allowedIds) {
            ps.setLong(idx++, id);
        }
        return idx;
    }

    private Warehouse mapRow(ResultSet rs) throws SQLException {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseId(rs.getLong("warehouse_id"));
        warehouse.setName(rs.getString("name"));
        warehouse.setLocation(rs.getString("location"));
        return warehouse;
    }
}
