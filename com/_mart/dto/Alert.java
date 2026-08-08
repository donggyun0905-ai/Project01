package com._mart.dto;

import java.time.LocalDateTime;

public class Alert {

    private Long alertId;
    private Long itemId;
    private String alertType; // 재고부족 / 재고초과 / 이상출고 / 예측알림
    private String message;
    private Boolean isResolved;
    private LocalDateTime createdAt;

    public Alert() {
    }

    public Alert(Long alertId, Long itemId, String alertType, String message, Boolean isResolved, LocalDateTime createdAt) {
        this.alertId = alertId;
        this.itemId = itemId;
        this.alertType = alertType;
        this.message = message;
        this.isResolved = isResolved;
        this.createdAt = createdAt;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getIsResolved() {
        return isResolved;
    }

    public void setIsResolved(Boolean isResolved) {
        this.isResolved = isResolved;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Alert{alertId=" + alertId + ", itemId=" + itemId + ", alertType='" + alertType
                + "', message='" + message + "', isResolved=" + isResolved + ", createdAt=" + createdAt + "}";
    }
}
