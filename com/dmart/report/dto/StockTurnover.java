package com.dmart.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class StockTurnover {
	private long itemId;
	private String itemName;
	private LocalDate inboundDate;
	private int currentStockQty;
	private BigDecimal dailyVelocity;
	private BigDecimal turnoverRatio;
	private String status;
	
	public StockTurnover() {}
	
	public StockTurnover(
			long itemId, 
			String itemName, 
			LocalDate inboundDate, 
			int currentStockQty,
			BigDecimal dailyVelocity, 
			BigDecimal turnoverRatio, 
			String status) {
		
		this.itemId = itemId;
		this.itemName = itemName;
		this.inboundDate = inboundDate;
		this.currentStockQty = currentStockQty;
		this.dailyVelocity = dailyVelocity;
		this.turnoverRatio = turnoverRatio;
		this.status =status;
		
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

	public LocalDate getInboundDate() {
		return inboundDate;
	}

	public void setInboundDate(LocalDate inboundDate) {
		this.inboundDate = inboundDate;
	}

	public int getCurrentStockQty() {
		return currentStockQty;
	}

	public void setCurrentStockQty(int currentStockQty) {
		this.currentStockQty = currentStockQty;
	}

	public BigDecimal getDailyVelocity() {
		return dailyVelocity;
	}

	public void setDailyVelocity(BigDecimal dailyVelocity) {
		this.dailyVelocity = dailyVelocity;
	}

	public BigDecimal getTurnoverRatio() {
		return turnoverRatio;
	}

	public void setTurnoverRatio(BigDecimal turnoverRatio) {
		this.turnoverRatio = turnoverRatio;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@Override
	public String toString() {
	    return "StockTurnover{" +
	            "itemId=" + itemId +
	            ", itemName='" + itemName + '\'' +
	            ", inboundDate=" + inboundDate +
	            ", currentStockQty=" + currentStockQty +
	            ", dailyVelocity=" + dailyVelocity +
	            ", turnoverRatio=" + turnoverRatio +
	            ", status='" + status + '\'' +
	            '}';
	}
}
