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
        return approval;
    }
}
