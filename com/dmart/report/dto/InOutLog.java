package com.dmart.report.dto;

import java.time.LocalDate;

public class InOutLog {
	private long lotId;
	private long itemId;
	private String itemName;
	private String type;
	private int quantity;
	private String partnerName;
	private String opName;
	private LocalDate processedAt;
	
	public InOutLog() {}
	
	public InOutLog(
			long lotId, 
			long itemId, 
			String itemName,
			String type,
			int quantity,
			String partnerName,
			String opName,
			LocalDate processedAt
			) {
		
		this.lotId = lotId;
		this.itemId = itemId;
		this.itemName = itemName;
		this.type = type;
		this.quantity = quantity;
		this.partnerName = partnerName;
		this.opName = opName;
		this.processedAt = processedAt;
		
	}

	public long getLotId() {
		return lotId;
	}

	public void setLotId(long lotId) {
		this.lotId = lotId;
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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public String getPartnerName() {
		return partnerName;
	}

	public void setPartnerName(String partnerName) {
		this.partnerName = partnerName;
	}

	public String getOpName() {
		return opName;
	}

	public void setOpName(String opName) {
		this.opName = opName;
	}

	public LocalDate getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(LocalDate processedAt) {
		this.processedAt = processedAt;
	}

	@Override
	public String toString() {
	    return "InOutLog{" +
	            "lotId=" + lotId +
	            ", itemId=" + itemId +
	            ", itemName='" + itemName + '\'' +
	            ", type='" + type + '\'' +
	            ", quantity=" + quantity +
	            ", partnerName='" + partnerName + '\'' +
	            ", opName='" + opName + '\'' +
	            ", processedAt=" + processedAt +
	            '}';
	}
	
}
