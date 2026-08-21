package com.dmart.report.dto;

public class ClientOutboundRanking {
	private int rank;
	private long partnerId;
	private String partnerName;
	private int totalQty;
	
	public ClientOutboundRanking() {}
	
	public ClientOutboundRanking(int rank, long partnerId, String partnerName, int totalQty) {
		this.rank = rank;
		this.partnerId = partnerId;
		this.partnerName = partnerName;
		this.totalQty = totalQty;
	}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
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

	public int getTotalQty() {
		return totalQty;
	}

	public void setTotalQty(int totalQty) {
		this.totalQty = totalQty;
	}

	@Override
	public String toString() {
	    return "ClientOutboundRanking{" +
	            "rank=" + rank +
	            ", partnerId=" + partnerId +
	            ", partnerName='" + partnerName + '\'' +
	            ", totalQty=" + totalQty +
	            '}';
	}
}
