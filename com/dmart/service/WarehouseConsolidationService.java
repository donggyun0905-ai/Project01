package com.dmart.service;

import com.dmart.dao.AlertDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 서버 시작 시 1회, 한 품목이 여러 구역에 나뉘어 있으면서 그중 일부 구역은 점유율이 낮아
// 다른 구역으로 합칠 수 있는 경우를 찾아 "창고정리추천" 알림만 만든다. ExpiryDisposalService와 같은
// "서버 시작 시 1회 배치" 패턴 — 출고/폐기 트랜잭션마다 매번 체크하면 알림이 너무 자주 쌓여서
// 오히려 관리자가 놓치기 쉬워짐(팀 논의 결론).
// 실제 이동은 하지 않음 — 창고 담당자가 확인 후 8번(TransferService)으로 직접 실행해야 함
// (자동으로 옮기면 실물 위치와 시스템이 어긋날 위험이 있음).
public class WarehouseConsolidationService {

    private static final double LOW_OCCUPANCY_THRESHOLD = 0.5; // 이 미만이면 "정리 대상" 후보로 봄
    private static final String ALERT_TYPE = "창고정리추천";

    // 메시지 어디에 있든 첫 번째로 나오는 zoneId가 "출발 구역"이다(사람이 읽는 라벨이 몇 개 더 섞여 있어도
    // 이 토큰 자체는 항상 그대로 남겨 두므로, 메시지 문구가 조금 바뀌어도 이 정규식은 영향받지 않는다).
    private static final Pattern MOVE_PATTERN = Pattern.compile("zoneId=(\\d+)");

    private final StockLotDao stockLotDao = new StockLotDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final AlertDao alertDao = new AlertDao();

    public static class Recommendation {
        public final Long itemId;
        public final Long fromZoneId;
        public final Long toZoneId;
        public final int quantity;
        public final int occupancyPercent;
        public final String fromLabel;
        public final String toLabel;

        public Recommendation(Long itemId, Long fromZoneId, Long toZoneId, int quantity, int occupancyPercent,
                               String fromLabel, String toLabel) {
            this.itemId = itemId;
            this.fromZoneId = fromZoneId;
            this.toZoneId = toZoneId;
            this.quantity = quantity;
            this.occupancyPercent = occupancyPercent;
            this.fromLabel = fromLabel;
            this.toLabel = toLabel;
        }
    }

    public int scan() throws SQLException {
        resolveStaleRecommendations();

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
                // 사람이 바로 알아볼 수 있게 "대형(0) EA" 같은 이름을 앞에 붙인다. zoneId 자체는
                // [ ] 안에 그대로 남겨 두는데(approval.html의 실행 파싱, resolveStaleRecommendations
                // 둘 다 이 토큰을 읽음), 화면에 보여줄 때는 plainText()(common.js)가 대괄호째 지운다
                // — 그래서 사람 눈에는 이름만 보이고, 실제 처리 대상은 항상 그 이름이 가리키는 곳과 같다.
                alert.setMessage("품목(itemId=" + r.itemId + ") 재고가 여러 구역에 분산되어 있습니다. "
                        + r.fromLabel + "[zoneId=" + r.fromZoneId + "](수량 " + r.quantity + ", 점유율 " + r.occupancyPercent
                        + "%)를 " + r.toLabel + "[zoneId=" + r.toZoneId + "]로 합치는 걸 추천합니다");
                alert.setIsResolved(false);
                alertDao.insert(conn, alert);
                createdCount++;
            }
        }
        return createdCount;
    }

    // existsUnresolvedByItemIdAndType는 "같은 품목에 처리 안 된 추천이 있는가"만 보기 때문에,
    // 예전 추천이 가리키는 출발 구역의 재고가 이미 다른 경로(출고/이동/반품폐기)로 빠져나가
    // 더 이상 유효하지 않은 채 남아있으면, 그 낡은 추천 하나가 같은 품목의 새로운(진짜 필요한)
    // 추천을 계속 가로막는 문제가 있었다. 승인 관리 화면에서 "지금 실행"을 눌러도 옮길 게 없으면
    // 그냥 해결 처리만 되는 것과 같은 기준(출발 구역 재고 0)으로, 스캔 시작 시점에 미리 정리해 둔다.
    private void resolveStaleRecommendations() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            List<Alert> unresolved = alertDao.findUnresolved(conn);
            for (Alert alert : unresolved) {
                if (!ALERT_TYPE.equals(alert.getAlertType())) {
                    continue;
                }
                String message = alert.getMessage();
                Matcher m = MOVE_PATTERN.matcher(message == null ? "" : message);
                if (!m.find()) {
                    continue;
                }
                Long fromZoneId = Long.valueOf(m.group(1));
                if (stockLotDao.sumQuantityByItemAndZone(conn, alert.getItemId(), fromZoneId) <= 0) {
                    alert.setIsResolved(true);
                    alertDao.update(conn, alert);
                }
            }
        }
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

        // WarehouseServlet의 GET 목록은 STAFF에게 배정 창고만 보여주지만(4번 참고), 이 알림 문구는
        // DAO를 직접 써서 만들기 때문에 그 제한과 무관하게 항상 창고 이름을 붙일 수 있다 —
        // 알림 자체는 로그인만 하면 누구나 보게 되어 있어(11번), 보는 사람의 창고 배정과 상관없이
        // 매번 같은 문구가 보여야 한다.
        Map<Long, Warehouse> warehouseCache = new HashMap<>();
        for (Warehouse warehouse : warehouseDao.findAll(conn)) {
            warehouseCache.put(warehouse.getWarehouseId(), warehouse);
        }

        List<Recommendation> result = new ArrayList<>();
        for (Map.Entry<Long, List<StockLotDao.ItemZoneQuantity>> entry : byItem.entrySet()) {
            List<StockLotDao.ItemZoneQuantity> zoneQuantities = entry.getValue();
            if (zoneQuantities.size() < 2) {
                continue; // 한 구역에만 있으면 정리할 게 없음
            }

            // 이 품목이 가장 많이 쌓여 있는 구역을 "본거지"로 정하고, 항상 그 구역으로만 합친다.
            // (구역마다 따로 "제일 많은 다른 구역"을 찾으면, 구역이 2개뿐일 때 자기보다 적은 쪽이
            //  유일한 후보라는 이유만으로 뽑혀서 "많은 쪽이 적은 쪽으로 합쳐지는" 역방향 추천이 나올 수 있음)
            StockLotDao.ItemZoneQuantity hub = null;
            for (StockLotDao.ItemZoneQuantity z : zoneQuantities) {
                if (hub == null || z.quantity > hub.quantity) {
                    hub = z;
                }
            }
            Zone hubZone = zoneCache.get(hub.zoneId);
            if (hubZone == null || hubZone.getCapacity() == null) {
                continue;
            }

            for (StockLotDao.ItemZoneQuantity source : zoneQuantities) {
                if (source.zoneId.equals(hub.zoneId)) {
                    continue; // 본거지 자기 자신은 정리 대상이 아님
                }
                Zone sourceZone = zoneCache.get(source.zoneId);
                if (sourceZone == null || sourceZone.getCapacity() == null || sourceZone.getCapacity() == 0) {
                    continue;
                }
                double occupancy = (double) source.quantity / sourceZone.getCapacity();
                if (occupancy >= LOW_OCCUPANCY_THRESHOLD) {
                    continue; // 이미 충분히 차 있으면 정리 대상 아님
                }

                // 목적지(본거지) 구역의 "전체(품목 무관)" 현재 재고 기준으로 용량 체크 —
                // InboundService/TransferService와 동일한 공유 물리공간 규칙.
                int hubOverallTotal = stockLotDao.sumQuantityByZoneId(conn, hub.zoneId);
                if (hubOverallTotal + source.quantity > hubZone.getCapacity()) {
                    continue; // 옮기면 본거지 용량 초과
                }

                int occupancyPercent = (int) Math.round(occupancy * 100);
                result.add(new Recommendation(entry.getKey(), source.zoneId, hub.zoneId,
                        source.quantity, occupancyPercent,
                        zoneLabel(sourceZone, warehouseCache), zoneLabel(hubZone, warehouseCache)));
            }
        }
        return result;
    }

    // "대형(0) EA" 같은 이름을 만든다(item.html의 findZoneName, approval.html의 zoneLabel과 같은 방식).
    private String zoneLabel(Zone zone, Map<Long, Warehouse> warehouseCache) {
        Warehouse warehouse = warehouseCache.get(zone.getWarehouseId());
        if (warehouse == null) {
            return zone.getZoneName();
        }
        return warehouse.getName() + "(" + warehouse.getLocation() + ") " + zone.getZoneName();
    }
}
