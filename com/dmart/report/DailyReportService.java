package com.dmart.report;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Item;
import com.dmart.dto.StockLot;
import com.dmart.report.dao.DailyReportDao;
import com.dmart.report.dto.DailyComparison;
import com.dmart.report.dto.LowStockItem;
import com.dmart.report.dto.TopOutboundItem;


public class DailyReportService {
	DailyReportDao dao = new DailyReportDao();
	
	AlertDao alertDao = new AlertDao();
	ItemDao itemDao = new ItemDao();
	StockLotDao stockLotDao = new StockLotDao();

	// 전일 대비 입출고 증감량 / 증감률
	public DailyComparison getYesterdayComparison(LocalDate searchDate) {
		if (searchDate == null)
			searchDate = LocalDate.now();

		try (Connection conn = DBConnection.getConnection()) {
			DailyComparison dailyComp = dao.selectDailyComparison(conn, searchDate);
			if (dailyComp == null)
				return null;

			int tdyInbound = dailyComp.getTodayInboundQty();
			int ydayInbound = dailyComp.getYesterdayInboundQty();
			int tdyOutbound = dailyComp.getTodayOutboundQty();
			int ydayOutbound = dailyComp.getYesterdayOutboundQty();

			int inboundChange = tdyInbound - ydayInbound;
			int outboundChange = tdyOutbound - ydayOutbound;

			double inboundChangeRate = (ydayInbound != 0) ? (tdyInbound - ydayInbound) / (double) ydayInbound * 100 : 0;
			double outboundChangeRate = (ydayOutbound != 0) ? (tdyOutbound - ydayOutbound) / (double) ydayOutbound * 100 : 0;

			dailyComp.setInboundQtyChange(inboundChange);
			dailyComp.setOutboundQtyChange(outboundChange);
			dailyComp.setInboundQtyChangeRate(inboundChangeRate);
			dailyComp.setOutboundQtyChangeRate(outboundChangeRate);

			return dailyComp;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	// LowStock(재고 부족) 품목 리스트
	public List<LowStockItem> getLowStockitems(LocalDate searchDate) {
		List<LowStockItem> result = new ArrayList<>();

		try (Connection conn = DBConnection.getConnection()) {
			List<Alert> alerts = alertDao.findUnresolved(conn);

			for (Alert alert : alerts) {
				if (!"재고부족".equals(alert.getAlertType()))
					continue;

				long itemId = alert.getItemId();

				Item item = itemDao.findById(conn, itemId);
				if (item == null)
					continue;
				
				// NORMAL 상태 로트만 합산
				List<StockLot> lots = stockLotDao.findByItemIdOrderByExpiryDate(conn, itemId);
				int currentQty = lots.stream().filter(lot -> "NORMAL".equals(lot.getStatus()))
						.mapToInt(StockLot::getQuantity).sum();

				result.add(new LowStockItem(itemId, item.getItemName(), currentQty, item.getThresholdMin()));

			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}
	
	// 금일 Top5 출고량 품목
	public List<TopOutboundItem> getTop5OutboundItems(LocalDate searchDate) {
	    if (searchDate == null) searchDate = LocalDate.now();
	    
	    try (Connection conn = DBConnection.getConnection()) {
	    	List<TopOutboundItem> topList = dao.selectTop5OutboundItems(conn, searchDate);
	    	if (topList == null || topList.isEmpty()) return new ArrayList<>();
	    	return topList;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
}
