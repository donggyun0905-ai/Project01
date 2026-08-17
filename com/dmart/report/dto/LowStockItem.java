package com.dmart.report.dto;

public class LowStockItem {
	private long itemId;
	private String itemName;
	private int currentQty;
	private int thresholdMin; // 하한선
	
	public LowStockItem() {}
	
	public LowStockItem(long itemId, String itemName, int currentQty, int thresholdMin) {
		this.itemId = itemId;
		this.itemName = itemName;
		this.currentQty = currentQty;
		this.thresholdMin = thresholdMin;
	}

	public long getItemId() {
		return itemId;
	}

	public void setItemId(int itemId) {
		this.itemId = itemId;
	}

	public String getItemName() {
		return itemName;
	}

	public void setItemName(String itemName) {
		this.itemName = itemName;
	}

	public int getCurrentQty() {
		return currentQty;
	}

	public void setCurrentQty(int currentQty) {
		this.currentQty = currentQty;
	}

	public int getThresholdMin() {
		return thresholdMin;
	}

	public void setThresholdMin(int thresholdMin) {
		this.thresholdMin = thresholdMin;
	}
	
	@Override
	public String toString() {
		return "LowStockItem{" + "itemId=" + itemId +
				", itemName='" + itemName + '\'' +
				", currentQty=" + currentQty +
				", thresholdMin=" + thresholdMin + '}';
	}
}
