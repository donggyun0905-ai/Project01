package com.dmart.report.dto;

public class LowStockItem {
	private long itemId;
	private String itemName;
	private int currentQty;
	private int minQty; // 재고 부족 판단 기준 수량
	
	public LowStockItem() {}
	
	public LowStockItem(long itemId, String itemName, int currentQty, int minQty) {
		this.itemId = itemId;
		this.itemName = itemName;
		this.currentQty = currentQty;
		this.minQty = minQty;
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

	public int getCurrentQty() {
		return currentQty;
	}

	public void setCurrentQty(int currentQty) {
		this.currentQty = currentQty;
	}

	public int getMinQty() {
		return minQty;
	}

	public void setMinQty(int minQty) {
		this.minQty = minQty;
	}

	@Override
	public String toString() {
		return "LowStockItem{" + "itemId=" + itemId +
				", itemName='" + itemName + '\'' +
				", currentQty=" + currentQty +
				", minQty=" + minQty + '}';
	}
}
