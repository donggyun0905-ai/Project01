package com.dmart.service;

import com.dmart.dao.AlertDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Zone;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 서버 시작 시 1회, 한 품목이 여러 구역에 나뉘어 있으면서 그중 일부 구역은 점유율이 낮아
// 다른 구역으로 합칠 수 있는 경우를 찾아 "창고정리추천" 알림만 만든다. ExpiryDisposalService와 같은
// "서버 시작 시 1회 배치" 패턴 — 출고/폐기 트랜잭션마다 매번 체크하면 알림이 너무 자주 쌓여서
// 오히려 관리자가 놓치기 쉬워짐(팀 논의 결론).
// 실제 이동은 하지 않음 — 창고 담당자가 확인 후 8번(TransferService)으로 직접 실행해야 함
// (자동으로 옮기면 실물 위치와 시스템이 어긋날 위험이 있음).
public class WarehouseConsolidationService {

    private static final double LOW_OCCUPANCY_THRESHOLD = 0.5; // 이 미만이면 "정리 대상" 후보로 봄
    private static final String ALERT_TYPE = "창고정리추천";

    private final StockLotDao stockLotDao = new StockLotDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final AlertDao alertDao = new AlertDao();

    public static class Recommendation {
        public final Long itemId;
        public final Long fromZoneId;
        public final Long toZoneId;
        public final int quantity;
        public final int occupancyPercent;

        public Recommendation(Long itemId, Long fromZoneId, Long toZoneId, int quantity, int occupancyPercent) {
            this.itemId = itemId;
            this.fromZoneId = fromZoneId;
            this.toZoneId = toZoneId;
            this.quantity = quantity;
            this.occupancyPercent = occupancyPercent;
        }
    }

    public int scan() throws SQLException {
        List<Recommendation> recommendations;
        try (Connection conn = DBConnection.getConnection()) {
            recommendations = findRecommendations(conn);
        }

        int createdCount = 0;
        for (Recommendation r : recommendations) {
            try (Connection conn = DBConnection.getConnection()) {
                if (alertDao.existsUnresolvedByItemIdAndType(conn, r.itemId, ALERT_TYPE)) {
                    continue; // 같은 품목에 처리 안 된 추천이 이미 있으면 또 안 만듦
                }
                Alert alert = new Alert();
                alert.setItemId(r.itemId);
                alert.setAlertType(ALERT_TYPE);
                alert.setMessage("품목(itemId=" + r.itemId + ") 재고가 여러 구역에 분산되어 있습니다. "
                        + "zoneId=" + r.fromZoneId + "(수량 " + r.quantity + ", 점유율 " + r.occupancyPercent
                        + "%)를 zoneId=" + r.toZoneId + "로 합치는 걸 추천합니다");
                alert.setIsResolved(false);
                alertDao.insert(conn, alert);
                createdCount++;
            }
        }
        return createdCount;
    }

    private List<Recommendation> findRecommendations(Connection conn) throws SQLException {
        List<StockLotDao.ItemZoneQuantity> rows = stockLotDao.sumQuantityGroupedByItemAndZone(conn);
        Map<Long, List<StockLotDao.ItemZoneQuantity>> byItem = new LinkedHashMap<>();
        for (StockLotDao.ItemZoneQuantity row : rows) {
            byItem.computeIfAbsent(row.itemId, k -> new ArrayList<>()).add(row);
        }

        Map<Long, Zone> zoneCache = new HashMap<>();
        for (Zone zone : zoneDao.findAll(conn)) {
            zoneCache.put(zone.getZoneId(), zone);
        }

        List<Recommendation> result = new ArrayList<>();
        for (Map.Entry<Long, List<StockLotDao.ItemZoneQuantity>> entry : byItem.entrySet()) {
            List<StockLotDao.ItemZoneQuantity> zoneQuantities = entry.getValue();
            if (zoneQuantities.size() < 2) {
                continue; // 한 구역에만 있으면 정리할 게 없음
            }

            for (StockLotDao.ItemZoneQuantity source : zoneQuantities) {
                Zone sourceZone = zoneCache.get(source.zoneId);
                if (sourceZone == null || sourceZone.getCapacity() == null || sourceZone.getCapacity() == 0) {
                    continue;
                }
                double occupancy = (double) source.quantity / sourceZone.getCapacity();
                if (occupancy >= LOW_OCCUPANCY_THRESHOLD) {
                    continue; // 이미 충분히 차 있으면 정리 대상 아님
                }

                StockLotDao.ItemZoneQuantity bestTarget = null;
                for (StockLotDao.ItemZoneQuantity candidate : zoneQuantities) {
                    if (candidate.zoneId.equals(source.zoneId)) {
                        continue;
                    }
                    Zone targetZone = zoneCache.get(candidate.zoneId);
                    if (targetZone == null || targetZone.getCapacity() == null) {
                        continue;
                    }
                    // 목적지 구역의 "전체(품목 무관)" 현재 재고 기준으로 용량 체크 —
                    // InboundService/TransferService와 동일한 공유 물리공간 규칙.
                    int targetOverallTotal = stockLotDao.sumQuantityByZoneId(conn, candidate.zoneId);
                    if (targetOverallTotal + source.quantity > targetZone.getCapacity()) {
                        continue; // 옮기면 목적지 용량 초과
                    }
                    if (bestTarget == null || candidate.quantity > bestTarget.quantity) {
                        bestTarget = candidate; // 이 품목이 이미 제일 많이 있는 구역으로 합치는 걸 우선
                    }
                }

                if (bestTarget != null) {
                    int occupancyPercent = (int) Math.round(occupancy * 100);
                    result.add(new Recommendation(entry.getKey(), source.zoneId, bestTarget.zoneId,
                            source.quantity, occupancyPercent));
                }
            }
        }
        return result;
    }
}
