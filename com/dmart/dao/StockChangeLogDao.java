package com.dmart.dao;

import com.dmart.dto.StockChangeLog;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockChangeLogDao {

    public Long insert(Connection conn, StockChangeLog log) throws SQLException {
        String sql = "INSERT INTO STOCK_CHANGE_LOG (lot_id, changed_by, change_type, before_value, "
                + "after_value, reason, is_reverted) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, log.getLotId());
            ps.setLong(2, log.getChangedBy());
            ps.setString(3, log.getChangeType());
            ps.setString(4, log.getBeforeValue());
            ps.setString(5, log.getAfterValue());
            ps.setString(6, log.getReason());
            ps.setBoolean(7, log.getIsReverted() != null ? log.getIsReverted() : false);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, StockChangeLog log) throws SQLException {
        String sql = "UPDATE STOCK_CHANGE_LOG SET lot_id = ?, changed_by = ?, change_type = ?, "
                + "before_value = ?, after_value = ?, reason = ?, is_reverted = ? WHERE log_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, log.getLotId());
            ps.setLong(2, log.getChangedBy());
            ps.setString(3, log.getChangeType());
            ps.setString(4, log.getBeforeValue());
            ps.setString(5, log.getAfterValue());
            ps.setString(6, log.getReason());
            ps.setBoolean(7, log.getIsReverted() != null ? log.getIsReverted() : false);
            ps.setLong(8, log.getLogId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long logId) throws SQLException {
        String sql = "DELETE FROM STOCK_CHANGE_LOG WHERE log_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, logId);
            return ps.executeUpdate() > 0;
        }
    }

    public StockChangeLog findById(Connection conn, Long logId) throws SQLException {
        String sql = "SELECT * FROM STOCK_CHANGE_LOG WHERE log_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // 10.4 복원 도중 같은 로그가 동시에 두 번 복원되는 것(중복 복원)을 막기 위한 락 조회.
    // StockLotAdjustmentService.restore()에서 사용.
    public StockChangeLog findByIdForUpdate(Connection conn, Long logId) throws SQLException {
        String sql = "SELECT * FROM STOCK_CHANGE_LOG WHERE log_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<StockChangeLog> findByLotId(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT * FROM STOCK_CHANGE_LOG WHERE lot_id = ? ORDER BY changed_at DESC";
        List<StockChangeLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public List<StockChangeLog> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM STOCK_CHANGE_LOG ORDER BY log_id";
        List<StockChangeLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 13번 목록 API용. lotId/changeType/기간 선택 필터(없으면 전체 감사 로그).
    // changeType/from/to는 화면(audit.html)에서 예전엔 "지금 페이지 안에서만" 걸러서
    // 페이지네이션과 안 맞았는데, 여기서 직접 걸러주면 total도 필터링된 기준으로 정확히 나온다.
    // keyword(품목명)/changedByKeyword(담당자 이름)는 audit.html 검색창용 - 각각 필요할 때만
    // STOCK_LOT+ITEM / APP_USER를 조인한다.
    public List<StockChangeLog> findPage(Connection conn, Long lotId, String changeType, LocalDate from, LocalDate to,
                                          String keyword, String changedByKeyword, int offset, int limit) throws SQLException {
        String sql = "SELECT scl.* FROM STOCK_CHANGE_LOG scl" + joinClause(keyword, changedByKeyword)
                + whereClause(lotId, changeType, from, to, keyword, changedByKeyword)
                + " ORDER BY scl.changed_at DESC LIMIT ? OFFSET ?";
        List<StockChangeLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, lotId, changeType, from, to, keyword, changedByKeyword);
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

    public int count(Connection conn, Long lotId, String changeType, LocalDate from, LocalDate to,
                      String keyword, String changedByKeyword) throws SQLException {
        String sql = "SELECT COUNT(*) FROM STOCK_CHANGE_LOG scl" + joinClause(keyword, changedByKeyword)
                + whereClause(lotId, changeType, from, to, keyword, changedByKeyword);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, lotId, changeType, from, to, keyword, changedByKeyword);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String joinClause(String keyword, String changedByKeyword) {
        StringBuilder sb = new StringBuilder();
        if (keyword != null) {
            sb.append(" JOIN STOCK_LOT sl ON scl.lot_id = sl.lot_id JOIN ITEM i ON sl.item_id = i.item_id");
        }
        if (changedByKeyword != null) {
            sb.append(" JOIN APP_USER u ON scl.changed_by = u.user_id");
        }
        return sb.toString();
    }

    private String whereClause(Long lotId, String changeType, LocalDate from, LocalDate to,
                                String keyword, String changedByKeyword) {
        StringBuilder sb = new StringBuilder();
        if (lotId != null) {
            sb.append(" AND scl.lot_id = ?");
        }
        if (changeType != null) {
            sb.append(" AND scl.change_type = ?");
        }
        if (from != null) {
            sb.append(" AND DATE(scl.changed_at) >= ?");
        }
        if (to != null) {
            sb.append(" AND DATE(scl.changed_at) <= ?");
        }
        if (keyword != null) {
            sb.append(" AND i.item_name LIKE ?");
        }
        if (changedByKeyword != null) {
            sb.append(" AND u.name LIKE ?");
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, Long lotId, String changeType,
                                  LocalDate from, LocalDate to, String keyword, String changedByKeyword) throws SQLException {
        int idx = startIndex;
        if (lotId != null) {
            ps.setLong(idx++, lotId);
        }
        if (changeType != null) {
            ps.setString(idx++, changeType);
        }
        if (from != null) {
            ps.setDate(idx++, Date.valueOf(from));
        }
        if (to != null) {
            ps.setDate(idx++, Date.valueOf(to));
        }
        if (keyword != null) {
            ps.setString(idx++, "%" + keyword + "%");
        }
        if (changedByKeyword != null) {
            ps.setString(idx++, "%" + changedByKeyword + "%");
        }
        return idx;
    }

    private StockChangeLog mapRow(ResultSet rs) throws SQLException {
        StockChangeLog log = new StockChangeLog();
        log.setLogId(rs.getLong("log_id"));
        log.setLotId(rs.getLong("lot_id"));
        log.setChangedBy(rs.getLong("changed_by"));
        log.setChangeType(rs.getString("change_type"));
        log.setBeforeValue(rs.getString("before_value"));
        log.setAfterValue(rs.getString("after_value"));
        log.setReason(rs.getString("reason"));
        log.setIsReverted(rs.getBoolean("is_reverted"));
        Timestamp changedAt = rs.getTimestamp("changed_at");
        if (changedAt != null) {
            log.setChangedAt(changedAt.toLocalDateTime());
        }
        return log;
    }
}
