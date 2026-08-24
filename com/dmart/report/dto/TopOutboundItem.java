package com.dmart.report.dto;

public class TopOutboundItem {
	private int rank;
	private long itemId;
	private String itemName;
	private int totalOutboundQty; // 해당 조회일의 품목별 총 출고량
	
	public TopOutboundItem() {}
	
	public TopOutboundItem(int rank, long itemId, String itemName, int totalOutboundQty) {
		this.rank = rank;
		this.itemId = itemId;
		this.itemName = itemName;
		this.totalOutboundQty = totalOutboundQty;
	}
	
	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
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

	public int getTotalOutboundQty() {
		return totalOutboundQty;
	}

	public void setTotalOutboundQty(int totalOutboundQty) {
		this.totalOutboundQty = totalOutboundQty;
	}

	@Override
	public String toString() {
		return "TopOutboundItem{" + "rank=" + rank +
				", itemId=" + itemId +
				", itemName='" + itemName + '\'' +
				", totalOutboundQty=" + totalOutboundQty + '}';
	}
	
}
