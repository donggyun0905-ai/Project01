package com.dmart.dao;

import com.dmart.dto.ReturnDisposal;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReturnDisposalDao {

    public Long insert(Connection conn, ReturnDisposal disposalRecord) throws SQLException {
        String sql = "INSERT INTO RETURN_DISPOSAL (lot_id, type, reason, quantity, processed_by, processed_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, disposalRecord.getLotId());
            ps.setString(2, disposalRecord.getType());
            ps.setString(3, disposalRecord.getReason());
            ps.setInt(4, disposalRecord.getQuantity());
            ps.setLong(5, disposalRecord.getProcessedBy());
            ps.setDate(6, Date.valueOf(disposalRecord.getProcessedDate()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, ReturnDisposal disposalRecord) throws SQLException {
        String sql = "UPDATE RETURN_DISPOSAL SET lot_id = ?, type = ?, reason = ?, quantity = ?, "
                + "processed_by = ?, processed_date = ? WHERE record_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, disposalRecord.getLotId());
            ps.setString(2, disposalRecord.getType());
            ps.setString(3, disposalRecord.getReason());
            ps.setInt(4, disposalRecord.getQuantity());
            ps.setLong(5, disposalRecord.getProcessedBy());
            ps.setDate(6, Date.valueOf(disposalRecord.getProcessedDate()));
            ps.setLong(7, disposalRecord.getRecordId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long recordId) throws SQLException {
        String sql = "DELETE FROM RETURN_DISPOSAL WHERE record_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            return ps.executeUpdate() > 0;
        }
    }

    // 재고 로트 삭제(10.3) 안전장치용: 이 로트가 한 번이라도 반품/폐기 처리된 적이 있는지.
    public boolean existsByLotId(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT 1 FROM RETURN_DISPOSAL WHERE lot_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public ReturnDisposal findById(Connection conn, Long recordId) throws SQLException {
        String sql = "SELECT * FROM RETURN_DISPOSAL WHERE record_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<ReturnDisposal> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM RETURN_DISPOSAL ORDER BY record_id";
        List<ReturnDisposal> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 반품/폐기 이력 화면용. RETURN_DISPOSAL엔 item_id가 없어서 STOCK_LOT을 조인해 거른다.
    // type/category/keyword/from/to는 화면(return.html)에서 예전엔 "지금 페이지 안에서만"
    // 걸러서 페이지네이션과 안 맞았는데, 여기서 걸러주면 total도 필터링된 기준으로 정확히 나온다.
    // category/keyword(품목명 검색)는 ITEM까지 한 번 더 조인해야 해서 함께 추가했다.
    public List<ReturnDisposal> findPage(Connection conn, Long itemId, String type, String category, String keyword,
                                          LocalDate from, LocalDate to, int offset, int limit) throws SQLException {
        String sql = "SELECT rd.* FROM RETURN_DISPOSAL rd JOIN STOCK_LOT sl ON rd.lot_id = sl.lot_id"
                + " JOIN ITEM i ON sl.item_id = i.item_id"
                + whereClause(itemId, type, category, keyword, from, to)
                + " ORDER BY rd.record_id DESC LIMIT ? OFFSET ?";
        List<ReturnDisposal> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, itemId, type, category, keyword, from, to);
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

    public int count(Connection conn, Long itemId, String type, String category, String keyword,
                      LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COUNT(*) FROM RETURN_DISPOSAL rd JOIN STOCK_LOT sl ON rd.lot_id = sl.lot_id"
                + " JOIN ITEM i ON sl.item_id = i.item_id"
                + whereClause(itemId, type, category, keyword, from, to);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, itemId, type, category, keyword, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String whereClause(Long itemId, String type, String category, String keyword,
                                LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder();
        if (itemId != null) {
            sb.append(" AND sl.item_id = ?");
        }
        if (type != null) {
            sb.append(" AND rd.type = ?");
        }
        if (category != null) {
            sb.append(" AND i.category = ?");
        }
        if (keyword != null) {
            sb.append(" AND i.item_name LIKE ?");
        }
        if (from != null) {
            sb.append(" AND rd.processed_date >= ?");
        }
        if (to != null) {
            sb.append(" AND rd.processed_date <= ?");
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, Long itemId, String type, String category,
                                  String keyword, LocalDate from, LocalDate to) throws SQLException {
        int idx = startIndex;
        if (itemId != null) {
            ps.setLong(idx++, itemId);
        }
        if (type != null) {
            ps.setString(idx++, type);
        }
        if (category != null) {
            ps.setString(idx++, category);
        }
        if (keyword != null) {
            ps.setString(idx++, "%" + keyword + "%");
        }
        if (from != null) {
            ps.setDate(idx++, Date.valueOf(from));
        }
        if (to != null) {
            ps.setDate(idx++, Date.valueOf(to));
        }
        return idx;
    }

    private ReturnDisposal mapRow(ResultSet rs) throws SQLException {
        ReturnDisposal disposalRecord = new ReturnDisposal();
        disposalRecord.setRecordId(rs.getLong("record_id"));
        disposalRecord.setLotId(rs.getLong("lot_id"));
        disposalRecord.setType(rs.getString("type"));
        disposalRecord.setReason(rs.getString("reason"));
        disposalRecord.setQuantity(rs.getInt("quantity"));
        disposalRecord.setProcessedBy(rs.getLong("processed_by"));
        disposalRecord.setProcessedDate(rs.getDate("processed_date").toLocalDate());
        return disposalRecord;
    }
}
