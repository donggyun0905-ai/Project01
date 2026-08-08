package com._mart.dao;

import com._mart.dto.Approval;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ApprovalDao {

    public Long insert(Connection conn, Approval approval) throws SQLException {
        String sql = "INSERT INTO APPROVAL (item_id, alert_id, request_type, requested_qty, status, "
                + "requested_by, approved_by, approved_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, approval.getItemId());
            setNullableLong(ps, 2, approval.getAlertId());
            ps.setString(3, approval.getRequestType());
            ps.setInt(4, approval.getRequestedQty());
            ps.setString(5, approval.getStatus() != null ? approval.getStatus() : "대기");
            setNullableLong(ps, 6, approval.getRequestedBy());
            setNullableLong(ps, 7, approval.getApprovedBy());
            ps.setTimestamp(8, approval.getApprovedAt() != null ? Timestamp.valueOf(approval.getApprovedAt()) : null);
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
                + "status = ?, requested_by = ?, approved_by = ?, approved_at = ? WHERE approval_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, approval.getItemId());
            setNullableLong(ps, 2, approval.getAlertId());
            ps.setString(3, approval.getRequestType());
            ps.setInt(4, approval.getRequestedQty());
            ps.setString(5, approval.getStatus());
            setNullableLong(ps, 6, approval.getRequestedBy());
            setNullableLong(ps, 7, approval.getApprovedBy());
            ps.setTimestamp(8, approval.getApprovedAt() != null ? Timestamp.valueOf(approval.getApprovedAt()) : null);
            ps.setLong(9, approval.getApprovalId());
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
