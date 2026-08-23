package com.dmart.report;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.dmart.db.DBConnection;
import com.dmart.report.dao.StatisticsDao;
import com.dmart.report.dto.ClientMonthlyTrend;
import com.dmart.report.dto.ClientOutboundRanking;
import com.dmart.report.dto.PeriodInOutStat;
import com.dmart.report.dto.StockTurnover;

public class StatisticsService {
	private final StatisticsDao dao = new StatisticsDao();
	
	public List<PeriodInOutStat> getInOutStatistics(String unit, LocalDate from, LocalDate to, String warehouseName) {
		if(unit == null || unit.isBlank()) unit = "day";
		
		if(to == null) to = LocalDate.now();
		if(from == null) from = to.minusDays(29);
		
		if(from.isAfter(to))
			throw new IllegalArgumentException("시작일은 종료일보다 이후일 수 없습니다.");
		
		if(warehouseName == null || warehouseName.isBlank() || "전체".equals(warehouseName)) warehouseName = null; 
		
		List<PeriodInOutStat> result = new ArrayList<>();
		
		try (Connection conn = DBConnection.getConnection()) {
			Map<String, Integer> inboundMap = dao.selectInboundByPeriod(conn, unit, from, to, warehouseName);
            Map<String, Integer> outboundMap = dao.selectOutboundByPeriod(conn, unit, from, to, warehouseName);
            
			if ("day".equals(unit)) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                    String key = d.format(fmt);
                    result.add(new PeriodInOutStat(key,
                            inboundMap.getOrDefault(key, 0),
                            outboundMap.getOrDefault(key, 0)));
                }
                
            } else if ("week".equals(unit)){
            	WeekFields weekFields = WeekFields.ISO;

                LocalDate current = from.with(
                        TemporalAdjusters.previousOrSame(
                                DayOfWeek.MONDAY
                        )
                );

                while (!current.isAfter(to)) {

                    int weekYear = current.get(weekFields.weekBasedYear());

                    int week = current.get(weekFields.weekOfWeekBasedYear());

                    String key = String.format("%04d-%02d", weekYear, week);

                    result.add(new PeriodInOutStat(
                            key,
                            inboundMap.getOrDefault(key, 0),
                            outboundMap.getOrDefault(key, 0)
                    ));

                    current = current.plusWeeks(1);
                }
            	
            } else if ("month".equals(unit)){
            	DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
            	
            	LocalDate current = from.withDayOfMonth(1);
            	LocalDate end = to.withDayOfMonth(1);
            	
            	while(!current.isAfter(end)) {
            		String key = current.format(fmt);
            		
            		result.add(new PeriodInOutStat(
            				key,
            				inboundMap.getOrDefault(key, 0),
            				outboundMap.getOrDefault(key, 0)
            		));
            		current = current.plusMonths(1);
            	}
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        return result;
	}
	
	// 재고 회전율 분석
	public List<StockTurnover> getStockTurnoverStatistics (){
		try (Connection conn = DBConnection.getConnection()) {
			return dao.selectTurnoverRatio(conn);
		} catch (SQLException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	public List<StockTurnover> getTop6StockTurnover() {
		List<StockTurnover> list = getStockTurnoverStatistics();
		return list.stream().limit(6).collect(Collectors.toList());
	}
	
	// Top5 거래처 출고 랭킹 
	public List<ClientOutboundRanking> getTop5Outbound(LocalDate from, LocalDate to) {
	    if (to == null) to = LocalDate.now();
	    if (from == null) from = to.minusDays(29);
	    
	    try (Connection conn = DBConnection.getConnection()) {
	    	return dao.selectTop5Outbound(conn, from, to);
		} catch (SQLException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	// 거래처 월별 추이 조회
	public List<ClientMonthlyTrend> getMonthlyTrend(String start, String end) {
		int currentYear = LocalDate.now().getYear();
		
		if (start == null || start.isEmpty()) start = currentYear + "-01";
	    if (end == null || end.isEmpty()) end = currentYear + "-12";

	    try (Connection conn = DBConnection.getConnection()) {
	        return dao.selectMonthlyTrend(conn, start, end);
	    } catch (SQLException e) {
	        e.printStackTrace();
	        return Collections.emptyList();
	    }
	}
}
