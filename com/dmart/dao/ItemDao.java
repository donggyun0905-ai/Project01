package com.dmart.dao;

import com.dmart.dto.Item;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDao {

    public Long insert(Connection conn, Item item) throws SQLException {
        String sql = "INSERT INTO ITEM (item_name, category, unit, threshold_min, capacity_max, shelf_life_days, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getItemName());
            ps.setString(2, item.getCategory());
            ps.setString(3, item.getUnit());
            setNullableInt(ps, 4, item.getThresholdMin());
            setNullableInt(ps, 5, item.getCapacityMax());
            setNullableInt(ps, 6, item.getShelfLifeDays());
            ps.setBoolean(7, item.getIsActive() != null ? item.getIsActive() : true);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, Item item) throws SQLException {
        String sql = "UPDATE ITEM SET item_name = ?, category = ?, unit = ?, threshold_min = ?, capacity_max = ?, shelf_life_days = ?, is_active = ? WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getItemName());
            ps.setString(2, item.getCategory());
            ps.setString(3, item.getUnit());
            setNullableInt(ps, 4, item.getThresholdMin());
            setNullableInt(ps, 5, item.getCapacityMax());
            setNullableInt(ps, 6, item.getShelfLifeDays());
            ps.setBoolean(7, item.getIsActive() != null ? item.getIsActive() : true);
            ps.setLong(8, item.getItemId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long itemId) throws SQLException {
        String sql = "DELETE FROM ITEM WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    public Item findById(Connection conn, Long itemId) throws SQLException {
        String sql = "SELECT * FROM ITEM WHERE item_id = ?";
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

    public List<Item> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM ITEM ORDER BY item_id";
        List<Item> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 목록 API(3번)의 페이지네이션 + category/keyword/active 필터용. count()와 WHERE 절을 공유한다.
    public List<Item> findPage(Connection conn, String category, String keyword, Boolean active,
                                int offset, int limit) throws SQLException {
        String sql = "SELECT * FROM ITEM" + whereClause(category, keyword, active) + " ORDER BY item_id LIMIT ? OFFSET ?";
        List<Item> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, category, keyword, active);
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

    public int count(Connection conn, String category, String keyword, Boolean active) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ITEM" + whereClause(category, keyword, active);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, category, keyword, active);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String whereClause(String category, String keyword, Boolean active) {
        StringBuilder sb = new StringBuilder();
        if (category != null) {
            sb.append(" AND category = ?");
        }
        if (keyword != null) {
            sb.append(" AND item_name LIKE ?");
        }
        if (active != null) {
            sb.append(" AND is_active = ?");
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, String category, String keyword, Boolean active) throws SQLException {
        int idx = startIndex;
        if (category != null) {
            ps.setString(idx++, category);
        }
        if (keyword != null) {
            ps.setString(idx++, "%" + keyword + "%");
        }
        if (active != null) {
            ps.setBoolean(idx++, active);
        }
        return idx;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private Item mapRow(ResultSet rs) throws SQLException {
        Item item = new Item();
        item.setItemId(rs.getLong("item_id"));
        item.setItemName(rs.getString("item_name"));
        item.setCategory(rs.getString("category"));
        item.setUnit(rs.getString("unit"));
        int thresholdMin = rs.getInt("threshold_min");
        item.setThresholdMin(rs.wasNull() ? null : thresholdMin);
        int capacityMax = rs.getInt("capacity_max");
        item.setCapacityMax(rs.wasNull() ? null : capacityMax);
        int shelfLifeDays = rs.getInt("shelf_life_days");
        item.setShelfLifeDays(rs.wasNull() ? null : shelfLifeDays);
        item.setIsActive(rs.getBoolean("is_active"));
        return item;
    }
}
