package com.dmart.report.dto;

// Top5. 출고 품목 DTO
public class TopOutboundItem {
	private int rank;
	private String itemName;
	private int totalOutboundQty; // 금일 출고량
	
	public TopOutboundItem() {}
	
	public TopOutboundItem(int rank, String itemName, int totalOutboundQty) {
		this.rank = rank;
		this.itemName = itemName;
		this.totalOutboundQty = totalOutboundQty;
	}
	
	public int getRank() {
		return rank;
	}
	
	public void setRank(int rank) {
		this.rank = rank;
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
				", itemName='" + itemName + '\'' +
				", totalOutboundQty=" + totalOutboundQty + '}';
	}
	
}
