package com.dmart.report.dto;

import java.math.BigDecimal;

public class ClientMonthlyTrend {
	private long partnerId;
	private String partnerName;
	private String yearMonth;
	private int monthlyQty;
	private int transactionCount;
	
	public ClientMonthlyTrend() {}
	
	public ClientMonthlyTrend(long partnerId, String partnerName, String yearMonth, int monthlyQty, int transactionCount) {
		this.partnerId = partnerId;
		this.partnerName = partnerName;
		this.yearMonth = yearMonth;
		this.monthlyQty = monthlyQty;
		this.transactionCount = transactionCount;
	}

	public long getPartnerId() {
		return partnerId;
	}

	public void setPartnerId(long partnerId) {
		this.partnerId = partnerId;
	}

	public String getPartnerName() {
		return partnerName;
	}

	public void setPartnerName(String partnerName) {
		this.partnerName = partnerName;
	}

	public String getYearMonth() {
		return yearMonth;
	}

	public void setYearMonth(String yearMonth) {
		this.yearMonth = yearMonth;
	}

	public int getMonthlyQty() {
		return monthlyQty;
	}

	public void setMonthlyQty(int monthlyQty) {
		this.monthlyQty = monthlyQty;
	}

	public int getTransactionCount() {
		return transactionCount;
	}

	public void setTransactionCount(int transactionCount) {
		this.transactionCount = transactionCount;
	}

	@Override
    public String toString() {
        return "ClientMonthlyTrend{" +
                "partnerId=" + partnerId +
                ", partnerName='" + partnerName + '\'' +
                ", yearMonth='" + yearMonth + '\'' +
                ", monthlyQty=" + monthlyQty +
                ", transactionCount=" + transactionCount +
                '}';
    }
}
