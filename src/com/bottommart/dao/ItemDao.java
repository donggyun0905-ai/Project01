package com.bottommart.dao;

import com.bottommart.dto.Item;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDao {

    public Long insert(Connection conn, Item item) throws SQLException {
        String sql = "INSERT INTO ITEM (item_name, category, unit, threshold_min, capacity_max) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getItemName());
            ps.setString(2, item.getCategory());
            ps.setString(3, item.getUnit());
            setNullableInt(ps, 4, item.getThresholdMin());
            setNullableInt(ps, 5, item.getCapacityMax());
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
        String sql = "UPDATE ITEM SET item_name = ?, category = ?, unit = ?, threshold_min = ?, capacity_max = ? WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getItemName());
            ps.setString(2, item.getCategory());
            ps.setString(3, item.getUnit());
            setNullableInt(ps, 4, item.getThresholdMin());
            setNullableInt(ps, 5, item.getCapacityMax());
            ps.setLong(6, item.getItemId());
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
        return item;
    }
}
