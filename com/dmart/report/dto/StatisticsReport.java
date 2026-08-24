package com.dmart.report.dto;

import java.util.List;

/*
 * StatisticsReport
 * 입출고량 집계 / 재고 회전율 / 거래처 랭킹 / 거래처 월별 추이 조회를 하나로 묶어 관리하는 상위DTO
 */

public class StatisticsReport {
	private List<PeriodInOutStat> totalInOut;
	private List<PeriodInOutStat> largeInOut;
	private List<PeriodInOutStat> mediumInOut;
	private List<PeriodInOutStat> smallInOut;
	private List<StockTurnover> stockTurnover;
	private List<ClientOutboundRanking> clientRanking;
	private List<ClientMonthlyTrend> clientMonthlyTrend;
	
	public StatisticsReport() {}

    public StatisticsReport(
            List<PeriodInOutStat> totalInOut,
            List<PeriodInOutStat> largeInOut,
            List<PeriodInOutStat> mediumInOut,
            List<PeriodInOutStat> smallInOut,
            List<StockTurnover> stockTurnover,
            List<ClientOutboundRanking> clientRanking,
            List<ClientMonthlyTrend> clientMonthlyTrend) {

        this.totalInOut = totalInOut;
        this.largeInOut = largeInOut;
        this.mediumInOut = mediumInOut;
        this.smallInOut = smallInOut;
        this.stockTurnover = stockTurnover;
        this.clientRanking = clientRanking;
        this.clientMonthlyTrend = clientMonthlyTrend;
    }
    
	public List<PeriodInOutStat> getTotalInOut() {
		return totalInOut;
	}

	public void setTotalInOut(List<PeriodInOutStat> totalInOut) {
		this.totalInOut = totalInOut;
	}

	public List<PeriodInOutStat> getLargeInOut() {
		return largeInOut;
	}

	public void setLargeInOut(List<PeriodInOutStat> largeInOut) {
		this.largeInOut = largeInOut;
	}

	public List<PeriodInOutStat> getMediumInOut() {
		return mediumInOut;
	}

	public void setMediumInOut(List<PeriodInOutStat> mediumInOut) {
		this.mediumInOut = mediumInOut;
	}

	public List<PeriodInOutStat> getSmallInOut() {
		return smallInOut;
	}

	public void setSmallInOut(List<PeriodInOutStat> smallInOut) {
		this.smallInOut = smallInOut;
	}

	public List<StockTurnover> getStockTurnover() {
		return stockTurnover;
	}

	public void setStockTurnover(List<StockTurnover> stockTurnover) {
		this.stockTurnover = stockTurnover;
	}

	public List<ClientOutboundRanking> getClientRanking() {
		return clientRanking;
	}

	public void setClientRanking(List<ClientOutboundRanking> clientRanking) {
		this.clientRanking = clientRanking;
	}

	public List<ClientMonthlyTrend> getClientMonthlyTrend() {
		return clientMonthlyTrend;
	}

	public void setClientMonthlyTrend(List<ClientMonthlyTrend> clientMonthlyTrend) {
		this.clientMonthlyTrend = clientMonthlyTrend;
	}

	@Override
    public String toString() {
        return "StatisticsReport{" +
                "totalInOut=" + totalInOut +
                "largeInOut=" + largeInOut +
                "mediumInOut=" + mediumInOut +
                "smallInOut=" + smallInOut +
                ", stockTurnover=" + stockTurnover +
                ", clientRanking=" + clientRanking +
                ", clientMonthlyTrend=" + clientMonthlyTrend +
                '}';
    }
}
