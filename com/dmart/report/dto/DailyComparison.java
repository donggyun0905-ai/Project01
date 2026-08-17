package com.dmart.report.dto;

import java.time.LocalDate;

// 전일 대비 증감 DTO
public class DailyComparison  {
	private LocalDate date;
	private int todayInboundQty;
	private int yesterdayInboundQty;
	private int todayOutboundQty;
	private int yesterdayOutboundQty;
	
	private int inboundQtyChange;
	private int outboundQtyChange;
	private double inboundQtyChangeRate;
	private double outboundQtyChangeRate;
	
	public DailyComparison() {}
	
	public DailyComparison(int todayInboundQty, int yesterdayInboundQty, int todayOutboundQty, int yesterdayOutboundQty) {
		this.todayInboundQty = todayInboundQty;
		this.yesterdayInboundQty = yesterdayInboundQty;
		this.todayOutboundQty = todayOutboundQty;
		this.yesterdayOutboundQty = yesterdayOutboundQty;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public int getTodayInboundQty() {
		return todayInboundQty;
	}

	public void setTodayInboundQty(int todayInboundQty) {
		this.todayInboundQty = todayInboundQty;
	}

	public int getYesterdayInboundQty() {
		return yesterdayInboundQty;
	}

	public void setYesterdayInboundQty(int yesterdayInboundQty) {
		this.yesterdayInboundQty = yesterdayInboundQty;
	}

	public int getTodayOutboundQty() {
		return todayOutboundQty;
	}

	public void setTodayOutboundQty(int todayOutboundQty) {
		this.todayOutboundQty = todayOutboundQty;
	}

	public int getYesterdayOutboundQty() {
		return yesterdayOutboundQty;
	}

	public void setYesterdayOutboundQty(int yesterdayOutboundQty) {
		this.yesterdayOutboundQty = yesterdayOutboundQty;
	}

	public int getInboundQtyChange() {
		return inboundQtyChange;
	}

	public void setInboundQtyChange(int inboundQtyChange) {
		this.inboundQtyChange = inboundQtyChange;
	}

	public int getOutboundQtyChange() {
		return outboundQtyChange;
	}

	public void setOutboundQtyChange(int outboundQtyChange) {
		this.outboundQtyChange = outboundQtyChange;
	}

	public double getInboundQtyChangeRate() {
		return inboundQtyChangeRate;
	}

	public void setInboundQtyChangeRate(double inboundQtyChangeRate) {
		this.inboundQtyChangeRate = inboundQtyChangeRate;
	}

	public double getOutboundQtyChangeRate() {
		return outboundQtyChangeRate;
	}

	public void setOutboundQtyChangeRate(double outboundQtyChangeRate) {
		this.outboundQtyChangeRate = outboundQtyChangeRate;
	}
	
	@Override
	public String toString() {
		return "DailyComparison{" + "date=" + date +
				", todayInboundQty=" + todayInboundQty +
				", yesterdayInboundQty=" + yesterdayInboundQty +
				", todayOutboundQty=" + todayOutboundQty +
				", yesterdayOutboundQty=" + yesterdayOutboundQty +
				", inboundQtyChange=" + inboundQtyChange +
				", outboundQtyChange=" + outboundQtyChange +
				", inboundQtyChangeRate=" + String.format("%.2f", inboundQtyChangeRate) + "%" +
				", outboundQtyChangeRate=" + String.format("%.2f", outboundQtyChangeRate) + "%" + '}';
	}
	
}
