package com.dmart.report.dto;

import java.time.LocalDate;
import java.util.List;

public class DailyReport {
	private LocalDate date;
	private DailyComparison dailyComp;
	private List<LowStockItem> lowStockItem;
	private List<TopOutboundItem> topOutItem;
	
	public DailyReport() {}
	
	public DailyReport(
			LocalDate date,
			DailyComparison dailyComp,
			List<LowStockItem> lowStockItem,
			List<TopOutboundItem> topOutItem
			) {
		
		this.date = date;
		this.dailyComp = dailyComp;
		this.lowStockItem = lowStockItem;
		this.topOutItem = topOutItem;
	}
	
	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public DailyComparison getDailyComp() {
		return dailyComp;
	}

	public void setDailyComp(DailyComparison dailyComp) {
		this.dailyComp = dailyComp;
	}

	public List<LowStockItem> getLowStockItem() {
		return lowStockItem;
	}

	public void setLowStockItem(List<LowStockItem> lowStockItem) {
		this.lowStockItem = lowStockItem;
	}

	public List<TopOutboundItem> getTopOutItem() {
		return topOutItem;
	}

	public void setTopOutItem(List<TopOutboundItem> topOutItem) {
		this.topOutItem = topOutItem;
	}
	
	@Override
	public String toString() {
		 return "DailyReport{" +
				 	"date=" + date +
	                "dailyComp=" + dailyComp +
	                ", lowStockItem=" + lowStockItem +
	                ", topOutItem=" + topOutItem +
	                '}';
	}
	
}
