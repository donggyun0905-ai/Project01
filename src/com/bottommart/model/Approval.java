package com.bottommart.model;

import java.time.LocalDateTime;

public class Approval {

    private Long approvalId;
    private Long itemId;
    private Long alertId;       // 수동 요청 시 NULL
    private String requestType; // 발주 / 출고
    private Integer requestedQty;
    private String status;      // 대기 / 승인 / 반려
    private Long requestedBy;   // 시스템 자동 제안 시 NULL
    private Long approvedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;

    public Approval() {
    }

    public Approval(Long approvalId, Long itemId, Long alertId, String requestType, Integer requestedQty,
                     String status, Long requestedBy, Long approvedBy, LocalDateTime requestedAt, LocalDateTime approvedAt) {
        this.approvalId = approvalId;
        this.itemId = itemId;
        this.alertId = alertId;
        this.requestType = requestType;
        this.requestedQty = requestedQty;
        this.status = status;
        this.requestedBy = requestedBy;
        this.approvedBy = approvedBy;
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public Integer getRequestedQty() {
        return requestedQty;
    }

    public void setRequestedQty(Integer requestedQty) {
        this.requestedQty = requestedQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    @Override
    public String toString() {
        return "Approval{approvalId=" + approvalId + ", itemId=" + itemId + ", alertId=" + alertId
                + ", requestType='" + requestType + "', requestedQty=" + requestedQty + ", status='" + status
                + "', requestedBy=" + requestedBy + ", approvedBy=" + approvedBy
                + ", requestedAt=" + requestedAt + ", approvedAt=" + approvedAt + "}";
    }
}
