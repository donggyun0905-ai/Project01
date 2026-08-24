package com.dmart.report.dto;

import java.time.LocalDateTime;

public class AlertHistory {
	private long alertId;
	private long itemId;
	private String itemName;
	private String alertType;
	private String message;
	private Boolean isResolved;
	private LocalDateTime createdAt;
	
	public AlertHistory() {}
	
	public AlertHistory(
			long alertId,
			long itemId,
			String itemName,
			String alertType,
			String message,
			Boolean isResolved,
			LocalDateTime createdAt
			) {
		
		this.alertId = alertId;
		this.itemId = itemId;
		this.itemName = itemName;
		this.alertType = alertType;
		this.message = message;
		this.isResolved = isResolved;
		this.createdAt = createdAt;
	}

	public long getAlertId() {
		return alertId;
	}

	public void setAlertId(long alertId) {
		this.alertId = alertId;
	}

	public long getItemId() {
		return itemId;
	}

	public void setItemId(long itemId) {
		this.itemId = itemId;
	}

	public String getItemName() {
		return itemName;
	}

	public void setItemName(String itemName) {
		this.itemName = itemName;
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
	    return "AlertHistory{" +
	            "alertId=" + alertId +
	            ", itemId=" + itemId +
	            ", itemName='" + itemName + '\'' +
	            ", alertType='" + alertType + '\'' +
	            ", message='" + message + '\'' +
	            ", isResolved=" + isResolved +
	            ", createdAt=" + createdAt +
	            '}';
	}
	
}
