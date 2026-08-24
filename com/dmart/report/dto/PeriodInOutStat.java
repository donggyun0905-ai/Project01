package com.dmart.report.dto;

public class PeriodInOutStat {
	private String period;
	private int inboundQty;
	private int outboundQty;
	
	public PeriodInOutStat() {}
	
	public PeriodInOutStat(String period, int inboundQty, int outboundQty) {
		this.period = period;
		this.inboundQty = inboundQty;
		this.outboundQty = outboundQty;
	}

	public String getPeriod() {
		return period;
	}

	public void setPeriod(String period) {
		this.period = period;
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

	@Override
    public String toString() {
        return "PeriodInOutStat{period='" + period + "', inboundQty=" + inboundQty
                + ", outboundQty=" + outboundQty + "}";
    }
}
