package com.dmart.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// 시뮬레이터/자동관리 버튼의 켜짐-꺼짐 상태. SYSTEM_TOGGLE 테이블 참고(schema.sql).
public class SystemToggleDao {

    public boolean isOn(Connection conn, String toggleName) throws SQLException {
        String sql = "SELECT is_on FROM SYSTEM_TOGGLE WHERE toggle_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toggleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("is_on");
            }
        }
    }

    // 두 토글 다 미리 INSERT 해 둔 고정 행이라(schema.sql) UPDATE만으로 충분하지만,
    // 혹시라도 행이 없는 경우까지 대비해 upsert로 만들어 둔다.
    public void setOn(Connection conn, String toggleName, boolean on) throws SQLException {
        String sql = "INSERT INTO SYSTEM_TOGGLE (toggle_name, is_on) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE is_on = VALUES(is_on)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toggleName);
            ps.setBoolean(2, on);
            ps.executeUpdate();
        }
    }
}