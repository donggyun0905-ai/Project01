package com.dmart.dao;

import com.dmart.dto.Zone;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ZoneDao {

    public Long insert(Connection conn, Zone zone) throws SQLException {
        String sql = "INSERT INTO ZONE (warehouse_id, zone_name, capacity) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, zone.getWarehouseId());
            ps.setString(2, zone.getZoneName());
            setNullableInt(ps, 3, zone.getCapacity());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, Zone zone) throws SQLException {
        String sql = "UPDATE ZONE SET warehouse_id = ?, zone_name = ?, capacity = ? WHERE zone_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, zone.getWarehouseId());
            ps.setString(2, zone.getZoneName());
            setNullableInt(ps, 3, zone.getCapacity());
            ps.setLong(4, zone.getZoneId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long zoneId) throws SQLException {
        String sql = "DELETE FROM ZONE WHERE zone_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, zoneId);
            return ps.executeUpdate() > 0;
        }
    }

    public Zone findById(Connection conn, Long zoneId) throws SQLException {
        String sql = "SELECT * FROM ZONE WHERE zone_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // 용량 체크(현재 합계 조회 -> 비교 -> insert) 도중 동시 요청이 끼어드는 걸 막기 위해
    // 트랜잭션 안에서 이 row를 잠그고 조회한다. InboundService/TransferService에서 사용.
    public Zone findByIdForUpdate(Connection conn, Long zoneId) throws SQLException {
        String sql = "SELECT * FROM ZONE WHERE zone_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Zone> findByWarehouseId(Connection conn, Long warehouseId) throws SQLException {
        String sql = "SELECT * FROM ZONE WHERE warehouse_id = ? ORDER BY zone_id";
        List<Zone> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public List<Zone> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM ZONE ORDER BY zone_id";
        List<Zone> result = new ArrayList<>();
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

    private Zone mapRow(ResultSet rs) throws SQLException {
        Zone zone = new Zone();
        zone.setZoneId(rs.getLong("zone_id"));
        zone.setWarehouseId(rs.getLong("warehouse_id"));
        zone.setZoneName(rs.getString("zone_name"));
        int capacity = rs.getInt("capacity");
        zone.setCapacity(rs.wasNull() ? null : capacity);
        return zone;
    }
}
