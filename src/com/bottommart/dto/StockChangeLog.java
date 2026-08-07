package com.bottommart.dto;

import java.time.LocalDateTime;

public class StockChangeLog {

    private Long logId;
    private Long lotId;
    private Long changedBy;
    private String changeType; // UPDATE / DELETE / RESTORE
    private String beforeValue; // JSON 스냅샷
    private String afterValue;  // JSON 스냅샷
    private String reason;
    private Boolean isReverted;
    private LocalDateTime changedAt;

    public StockChangeLog() {
    }

    public StockChangeLog(Long logId, Long lotId, Long changedBy, String changeType, String beforeValue,
                           String afterValue, String reason, Boolean isReverted, LocalDateTime changedAt) {
        this.logId = logId;
        this.lotId = lotId;
        this.changedBy = changedBy;
        this.changeType = changeType;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.reason = reason;
        this.isReverted = isReverted;
        this.changedAt = changedAt;
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Long changedBy) {
        this.changedBy = changedBy;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getIsReverted() {
        return isReverted;
    }

    public void setIsReverted(Boolean isReverted) {
        this.isReverted = isReverted;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    @Override
    public String toString() {
        return "StockChangeLog{logId=" + logId + ", lotId=" + lotId + ", changedBy=" + changedBy
                + ", changeType='" + changeType + "', reason='" + reason + "', isReverted=" + isReverted
                + ", changedAt=" + changedAt + "}";
    }
}
