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
import java.util.Comparator;
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
	
	// 입출고량 집계
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

	// 재고 소진 예상 - 통계 페이지 입출고량 집계 카드 옆에 보여줄 예측 데이터.
	// 이미 계산돼 있는 일평균 소진 속도(dailyVelocity, 최근 90일 총 출고량/90)를 그대로 써서
	// "이 추세면 현재 재고가 며칠 뒤 바닥나는지"를 품목별로 구하고, 가장 임박한 순으로 상위
	// limit개만 돌려준다. 최근 90일 출고 이력이 없는(dailyVelocity=0, DEAD_STOCK) 품목은
	// "소진 예상"이 의미가 없으므로(분모가 0이라 나눌 수도 없음) 제외한다.
	public List<StockTurnover> getStockoutForecast(int limit) {
		List<StockTurnover> list = getStockTurnoverStatistics();
		list.removeIf(t -> t.getDailyVelocity() == null || t.getDailyVelocity().doubleValue() <= 0);
		list.sort(Comparator.comparingDouble(t -> t.getCurrentStockQty() / t.getDailyVelocity().doubleValue()));
		return list.stream().limit(limit).collect(Collectors.toList());
	}

	// 재고초과 임박 예상 - 재고 소진 예상과 짝을 이루는 반대쪽 위험. 일평균 입고 속도
	// (inboundDailyVelocity, 최근 90일 총 입고량/90)를 기준으로 "이 추세면 며칠 뒤
	// capacity_max를 넘는지" 계산해서 가장 임박한 순으로 상위 limit개만 돌려준다.
	// capacity_max가 없는 품목, 입고 이력이 없는(inboundDailyVelocity=0) 품목,
	// 이미 capacity_max를 넘은 품목(그건 예상이 아니라 이미 재고초과 알림 대상)은 제외한다.
	public List<StockTurnover> getOverstockForecast(int limit) {
		List<StockTurnover> list = getStockTurnoverStatistics();
		list.removeIf(t -> t.getCapacityMax() == null
				|| t.getInboundDailyVelocity() == null || t.getInboundDailyVelocity().doubleValue() <= 0
				|| t.getCurrentStockQty() >= t.getCapacityMax());
		list.sort(Comparator.comparingDouble(
				t -> (t.getCapacityMax() - t.getCurrentStockQty()) / t.getInboundDailyVelocity().doubleValue()));
		return list.stream().limit(limit).collect(Collectors.toList());
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
	
	public Map<String, Integer> getWarehouseStock() {
		try(Connection conn = DBConnection.getConnection()) {
			return dao.selectWarehouseStock(conn);
		} catch (SQLException e) {
			e.printStackTrace();
			return Map.of();
		}
	}
}
