package com.dmart.report.dto;

public class ItemExportRow {
	private long itemId;
	private String itemName;
	private String category;
	private String unit;
	private Integer shelfLifeDays;
	private int totalStock;
	private Double turnoverRatio;
	private int inboundQty;
	private int outboundQty;

	public ItemExportRow() {}

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

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public Integer getShelfLifeDays() {
		return shelfLifeDays;
	}

	public void setShelfLifeDays(Integer shelfLifeDays) {
		this.shelfLifeDays = shelfLifeDays;
	}

	public int getTotalStock() {
		return totalStock;
	}

	public void setTotalStock(int totalStock) {
		this.totalStock = totalStock;
	}

	public Double getTurnoverRatio() {
		return turnoverRatio;
	}

	public void setTurnoverRatio(Double turnoverRatio) {
		this.turnoverRatio = turnoverRatio;
	}

	public int getInboundQty() {
		return inboundQty;
	}

	public void setInboundQty(int inboundQty) {
		this.inboundQty = inboundQty;
	}

	public int getOutboundQty() {
		return outboundQty;
	}

	public void setOutboundQty(int outboundQty) {
		this.outboundQty = outboundQty;
	}
}
