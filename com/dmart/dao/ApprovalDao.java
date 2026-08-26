package com.dmart.dao;

import com.dmart.dto.Approval;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ApprovalDao {

    public Long insert(Connection conn, Approval approval) throws SQLException {
        String sql = "INSERT INTO APPROVAL (item_id, alert_id, request_type, requested_qty, partner_id, status, "
                + "requested_by, approved_by, approved_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, approval.getItemId());
            setNullableLong(ps, 2, approval.getAlertId());
            ps.setString(3, approval.getRequestType());
            ps.setInt(4, approval.getRequestedQty());
            setNullableLong(ps, 5, approval.getPartnerId());
            ps.setString(6, approval.getStatus() != null ? approval.getStatus() : "대기");
            setNullableLong(ps, 7, approval.getRequestedBy());
            setNullableLong(ps, 8, approval.getApprovedBy());
            ps.setTimestamp(9, approval.getApprovedAt() != null ? Timestamp.valueOf(approval.getApprovedAt()) : null);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, Approval approval) throws SQLException {
        String sql = "UPDATE APPROVAL SET item_id = ?, alert_id = ?, request_type = ?, requested_qty = ?, "
                + "partner_id = ?, status = ?, requested_by = ?, approved_by = ?, approved_at = ? WHERE approval_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, approval.getItemId());
            setNullableLong(ps, 2, approval.getAlertId());
            ps.setString(3, approval.getRequestType());
            ps.setInt(4, approval.getRequestedQty());
            setNullableLong(ps, 5, approval.getPartnerId());
            ps.setString(6, approval.getStatus() != null ? approval.getStatus() : "대기");
            setNullableLong(ps, 7, approval.getRequestedBy());
            setNullableLong(ps, 8, approval.getApprovedBy());
            ps.setTimestamp(9, approval.getApprovedAt() != null ? Timestamp.valueOf(approval.getApprovedAt()) : null);
            ps.setLong(10, approval.getApprovalId());
            return ps.executeUpdate() > 0;
        }
    }

    // 승인 처리 후 실제로 얼마나 처리됐는지(발주: 성공 시 requested_qty, 실패 시 0 /
    // 출고: 실제로 나간 수량)를 따로 기록한다 - 결정(status/approved_at) 저장과는 별도
    // 단계(실행)에서 알게 되는 값이라 insert/update 전체가 아니라 이 값만 갱신한다.
    public void updateFulfilledQty(Connection conn, Long approvalId, Integer fulfilledQty) throws SQLException {
        String sql = "UPDATE APPROVAL SET fulfilled_qty = ? WHERE approval_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (fulfilledQty == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, fulfilledQty);
            }
            ps.setLong(2, approvalId);
            ps.executeUpdate();
        }
    }

    public boolean deleteById(Connection conn, Long approvalId) throws SQLException {
        String sql = "DELETE FROM APPROVAL WHERE approval_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, approvalId);
            return ps.executeUpdate() > 0;
        }
    }

    // 승인/반려 처리 중 동시에 같은 건이 두 번 처리되는 것(이중 자동실행)을 막기 위한 락 조회.
    // ApprovalService.decide()에서 사용.
    public Approval findByIdForUpdate(Connection conn, Long approvalId) throws SQLException {
        String sql = "SELECT * FROM APPROVAL WHERE approval_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, approvalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Approval findById(Connection conn, Long approvalId) throws SQLException {
        String sql = "SELECT * FROM APPROVAL WHERE approval_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, approvalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Approval> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM APPROVAL ORDER BY approval_id";
        List<Approval> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 12번 목록 API용. status/requestType 선택 필터.
    // requestType은 outbound.html의 "출고 요청" 탭처럼 발주/출고 중 한쪽만 보고 싶을 때 씀.
    public List<Approval> findPage(Connection conn, String status, String requestType, int offset, int limit) throws SQLException {
        String sql = "SELECT * FROM APPROVAL" + whereClause(status, requestType) + " ORDER BY requested_at DESC LIMIT ? OFFSET ?";
        List<Approval> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, status, requestType);
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

    public int count(Connection conn, String status, String requestType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM APPROVAL" + whereClause(status, requestType);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, status, requestType);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String whereClause(String status, String requestType) {
        StringBuilder sb = new StringBuilder();
        if (status != null) {
            sb.append(" AND status = ?");
        }
        if (requestType != null) {
            sb.append(" AND request_type = ?");
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, String status, String requestType) throws SQLException {
        int idx = startIndex;
        if (status != null) {
            ps.setString(idx++, status);
        }
        if (requestType != null) {
            ps.setString(idx++, requestType);
        }
        return idx;
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private Approval mapRow(ResultSet rs) throws SQLException {
        Approval approval = new Approval();
        approval.setApprovalId(rs.getLong("approval_id"));
        approval.setItemId(rs.getLong("item_id"));
        long alertId = rs.getLong("alert_id");
        approval.setAlertId(rs.wasNull() ? null : alertId);
        approval.setRequestType(rs.getString("request_type"));
        approval.setRequestedQty(rs.getInt("requested_qty"));
        long partnerId = rs.getLong("partner_id");
        approval.setPartnerId(rs.wasNull() ? null : partnerId);
        approval.setStatus(rs.getString("status"));
        long requestedBy = rs.getLong("requested_by");
        approval.setRequestedBy(rs.wasNull() ? null : requestedBy);
        long approvedBy = rs.getLong("approved_by");
        approval.setApprovedBy(rs.wasNull() ? null : approvedBy);
        Timestamp requestedAt = rs.getTimestamp("requested_at");
        if (requestedAt != null) {
            approval.setRequestedAt(requestedAt.toLocalDateTime());
        }
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        if (approvedAt != null) {
            approval.setApprovedAt(approvedAt.toLocalDateTime());
        }
        int fulfilledQty = rs.getInt("fulfilled_qty");
        approval.setFulfilledQty(rs.wasNull() ? null : fulfilledQty);
        return approval;
    }
}
