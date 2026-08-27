package com.dmart.report.dto;

import java.time.LocalDate;
import java.util.Map;

public class DailyComparison  {
	private LocalDate date;
	private int todayInboundQty;
	private int yesterdayInboundQty;
	private int todayOutboundQty;
	private int yesterdayOutboundQty;

	private int inboundQtyChange;
	private int outboundQtyChange;

	// 전일 수량이 0인 경우 증감률을 null로 처리하기 위해 Double 사용
	private Double inboundQtyChangeRate;
	private Double outboundQtyChangeRate;

	// 대시보드 카드 옆 팔레트/박스/낱개 소분류용 - 키는 zone_name(PALLET/BOX/EA) 그대로
	private Map<String, Integer> inboundByUnit;
	private Map<String, Integer> outboundByUnit;

	public DailyComparison() {}
	
	public DailyComparison(int todayInboundQty, int yesterdayInboundQty, int todayOutboundQty, int yesterdayOutboundQty) {
		this.todayInboundQty = todayInboundQty;
		this.yesterdayInboundQty = yesterdayInboundQty;
		this.todayOutboundQty = todayOutboundQty;
		this.yesterdayOutboundQty = yesterdayOutboundQty;
	}
	
	 public DailyComparison(
	            LocalDate date,
	            int todayInboundQty,
	            int yesterdayInboundQty,
	            int todayOutboundQty,
	            int yesterdayOutboundQty,
	            int inboundQtyChange,
	            int outboundQtyChange,
	            Double inboundQtyChangeRate,
	            Double outboundQtyChangeRate) {

	        this.date = date;
	        this.todayInboundQty = todayInboundQty;
	        this.yesterdayInboundQty = yesterdayInboundQty;
	        this.todayOutboundQty = todayOutboundQty;
	        this.yesterdayOutboundQty = yesterdayOutboundQty;
	        this.inboundQtyChange = inboundQtyChange;
	        this.outboundQtyChange = outboundQtyChange;
	        this.inboundQtyChangeRate = inboundQtyChangeRate;
	        this.outboundQtyChangeRate = outboundQtyChangeRate;
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

	 public Double getInboundQtyChangeRate() {
		 return inboundQtyChangeRate;
	 }

	 public void setInboundQtyChangeRate(Double inboundQtyChangeRate) {
		 this.inboundQtyChangeRate = inboundQtyChangeRate;
	 }

	 public Double getOutboundQtyChangeRate() {
		 return outboundQtyChangeRate;
	 }

	 public void setOutboundQtyChangeRate(Double outboundQtyChangeRate) {
		 this.outboundQtyChangeRate = outboundQtyChangeRate;
	 }

	 public Map<String, Integer> getInboundByUnit() {
		 return inboundByUnit;
	 }

	 public void setInboundByUnit(Map<String, Integer> inboundByUnit) {
		 this.inboundByUnit = inboundByUnit;
	 }

	 public Map<String, Integer> getOutboundByUnit() {
		 return outboundByUnit;
	 }

	 public void setOutboundByUnit(Map<String, Integer> outboundByUnit) {
		 this.outboundByUnit = outboundByUnit;
	 }

	 @Override
	 public String toString() {
	     return "DailyComparison{" +
	             "date=" + date +
	             ", todayInboundQty=" + todayInboundQty +
	             ", yesterdayInboundQty=" + yesterdayInboundQty +
	             ", todayOutboundQty=" + todayOutboundQty +
	             ", yesterdayOutboundQty=" + yesterdayOutboundQty +
	             ", inboundQtyChange=" + inboundQtyChange +
	             ", outboundQtyChange=" + outboundQtyChange +
	             ", inboundQtyChangeRate=" +
	             (inboundQtyChangeRate == null ? "-"  : String.format("%.2f%%", inboundQtyChangeRate)) +
	             ", outboundQtyChangeRate=" +
	             (outboundQtyChangeRate == null ? "-" : String.format("%.2f%%", outboundQtyChangeRate)) + '}';
	 }
	
}
