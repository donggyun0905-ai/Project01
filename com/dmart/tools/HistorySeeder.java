package com.dmart.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.dmart.db.DBConnection;

/**
 * 통계/보고서/알림/승인/감사로그/이동 이력 - 조회(기간 검색) 기능이 있는 화면들이
 * 전부 1년치 데이터를 보여주도록 한 번에 채워 넣는 독립 실행 도구입니다.
 * 앱에 배포되는 파일이 아니라 Eclipse에서 오른쪽 클릭 -> Run As -> Java Application
 * 으로 한 번 실행하고 버리는 스크립트라서, 다른 파일은 하나도 안 건드립니다.
 *
 * 채워 넣는 테이블: STOCK_LOT(입고 이력) / OUTBOUND(출고) / RETURN_DISPOSAL(반품·폐기)
 * / ALERT(알림) / APPROVAL(승인요청) / STOCK_TRANSFER(창고 간 이동) / STOCK_CHANGE_LOG(감사로그)
 *
 * 입고·출고·반품폐기 수량/확률은 SimulatorService와 완전히 같습니다:
 *   - 출고 1건당 1~20개, 입고 1건당 10~99개, 반품/폐기 1건당 1~5개
 *   - 반품/폐기 건수는 그날 출고 건수의 10/45 (시뮬레이터 확률표: 출고45 : 반품폐기10)
 * 하루 입고 총량 / 출고 총량은 각각 약 3000개(+-100)에 맞춥니다.
 *
 * 알림 문구는 실제 서비스(OutboundService/InboundService/ApprovalService/
 * WarehouseConsolidationService)가 만드는 것과 똑같은 형식을 씁니다 - 그래야
 * 대시보드의 문구 정리 로직(prettyAlertMessage)이 이 이력에도 똑같이 먹습니다.
 *
 * 입고 이력 로트는 지금 남아있는 재고(quantity)에 영향을 주면 안 되므로
 * status='DISPOSED', quantity=0 으로 넣습니다. 다만 "재고부족 -> 자동 발주 승인
 * -> 자동 입고" 흐름을 재현할 때 생기는 로트도 같은 방식(0/DISPOSED)으로 넣어서,
 * 알림/승인/입고 이력은 다 남기면서 지금 재고는 안 건드립니다.
 */
public class HistorySeeder {

    private static final LocalDate START = LocalDate.of(2026, 8, 26);
    private static final LocalDate END = LocalDate.of(2026, 8, 26); // 8/26 이후는 "현재 재고"와 안 겹치게 하루 비워둠

    private static final int DAILY_TARGET = 3000;
    private static final int DAILY_WOBBLE = 100; // +-100

    private static final Random random = new Random(42); // 매번 같은 결과가 나오게 고정 시드

    private static Map<Integer, String> itemUnit;
    private static Map<Integer, Integer> itemThresholdMin;
    private static Map<Integer, Integer> itemCapacityMax;
    private static Map<String, List<Integer>> zonesByUnit;
    private static Map<Integer, Integer> zoneWarehouseId; // zone_id -> warehouse_id (이동/정리추천 문구용)
    private static Map<Integer, String> warehouseLabel;   // warehouse_id -> "대형(0)" 같은 라벨

    private static List<Integer> suppliers;
    private static List<Integer> customers;
    private static List<Integer> staffUsers;
    private static List<int[]> items; // [item_id, shelf_life_days(-1=없음)]
    private static List<Long> allLotIds;

    private static long nextLotId, nextOutboundId, nextReturnId, nextAlertId, nextApprovalId, nextTransferId, nextLogId;

    public static void main(String[] args) throws SQLException {

        try (Connection conn = DBConnection.getConnection()) {

            conn.setAutoCommit(false);

            loadReferenceData(conn);

            String lotSql = "INSERT INTO STOCK_LOT "
                    + "(lot_id, item_id, zone_id, partner_id, quantity, initial_quantity, inbound_date, expiry_date, status, created_by) "
                    + "VALUES (?, ?, ?, ?, 0, ?, ?, ?, 'DISPOSED', ?)";
            String outSql = "INSERT INTO OUTBOUND (outbound_id, lot_id, partner_id, quantity, outbound_date, created_by) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            String retSql = "INSERT INTO RETURN_DISPOSAL (record_id, lot_id, type, reason, quantity, processed_by, processed_date) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            String alertSql = "INSERT INTO ALERT (alert_id, item_id, alert_type, message, is_resolved, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            String approvalSql = "INSERT INTO APPROVAL "
                    + "(approval_id, item_id, alert_id, request_type, requested_qty, partner_id, status, requested_by, approved_by, requested_at, approved_at, fulfilled_qty) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String transferSql = "INSERT INTO STOCK_TRANSFER (transfer_id, lot_id, from_zone_id, to_zone_id, quantity, handler_id, moved_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            String logSql = "INSERT INTO STOCK_CHANGE_LOG (log_id, lot_id, changed_by, change_type, before_value, after_value, reason, is_reverted, changed_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            int[] counts = new int[9]; // lot, out, ret, alert, approval, transfer, log
            int dayCount = 0;

            try (PreparedStatement lotPs = conn.prepareStatement(lotSql);
                 PreparedStatement outPs = conn.prepareStatement(outSql);
                 PreparedStatement retPs = conn.prepareStatement(retSql);
                 PreparedStatement alertPs = conn.prepareStatement(alertSql);
                 PreparedStatement approvalPs = conn.prepareStatement(approvalSql);
                 PreparedStatement transferPs = conn.prepareStatement(transferSql);
                 PreparedStatement logPs = conn.prepareStatement(logSql)) {

                for (LocalDate d = START; !d.isAfter(END); d = d.plusDays(1)) {

                    dayCount++;

                    int outCountToday = doInboundAndOutbound(d, lotPs, outPs, counts);
                    doReturnDisposal(d, outCountToday, retPs, counts);
                    doShortageChain(d, lotPs, alertPs, approvalPs, counts); // 재고부족 -> 자동발주 승인 -> 자동입고
                    doOverstockAlerts(d, alertPs, counts);                 // 재고초과
                    doAbnormalOutboundChain(d, alertPs, approvalPs, counts); // 이상출고 -> 출고 승인요청
                    doAutoFailAlerts(d, alertPs, counts);                  // 자동실행실패 (가끔)
                    doConsolidationAlerts(d, alertPs, counts);             // 창고정리추천
                    doTransfers(d, transferPs, counts);                   // 창고 간 이동
                    doChangeLogs(d, logPs, counts);                       // 감사로그

                    if (dayCount % 30 == 0) {
                        executeAll(lotPs, outPs, retPs, alertPs, approvalPs, transferPs, logPs);
                        conn.commit();
                        System.out.println(d + " 까지 진행... (로트" + counts[0] + " 출고" + counts[1] + " 반품폐기" + counts[2]
                                + " 알림" + counts[3] + " 승인" + counts[4] + " 이동" + counts[5] + " 감사로그" + counts[6] + ")");
                    }
                }

                executeAll(lotPs, outPs, retPs, alertPs, approvalPs, transferPs, logPs);
                conn.commit();
            }

            System.out.println("=====================================");
            System.out.println("완료! " + dayCount + "일치 생성됨");
            System.out.println("입고 로트: " + counts[0] + "건");
            System.out.println("출고: " + counts[1] + "건");
            System.out.println("반품/폐기: " + counts[2] + "건");
            System.out.println("알림: " + counts[3] + "건");
            System.out.println("승인요청: " + counts[4] + "건");
            System.out.println("창고 간 이동: " + counts[5] + "건");
            System.out.println("감사로그: " + counts[6] + "건");
        }
    }

    private static void executeAll(PreparedStatement... psList) throws SQLException {
        for (PreparedStatement ps : psList) {
            ps.executeBatch();
        }
    }


    /* ============================================================
       입고 / 출고 (기존과 동일한 로직)
       ============================================================ */
    private static int doInboundAndOutbound(LocalDate d, PreparedStatement lotPs, PreparedStatement outPs, int[] counts) throws SQLException {

        int inTarget = DAILY_TARGET + random.nextInt(DAILY_WOBBLE * 2 + 1) - DAILY_WOBBLE;
        int inRemaining = inTarget;

        while (inRemaining > 0) {
            int amt = Math.min(inRemaining, 10 + random.nextInt(90));
            int[] item = items.get(random.nextInt(items.size()));
            long lotId = insertHistoricalLot(lotPs, item[0], amt, d, item[1]);
            allLotIds.add(lotId);
            counts[0]++;
            inRemaining -= amt;
        }

        int outTarget = DAILY_TARGET + random.nextInt(DAILY_WOBBLE * 2 + 1) - DAILY_WOBBLE;
        int outRemaining = outTarget;
        int outCountToday = 0;

        while (outRemaining > 0) {
            int amt = Math.min(outRemaining, 1 + random.nextInt(20));
            long lotId = allLotIds.get(random.nextInt(allLotIds.size()));
            int customerId = customers.get(random.nextInt(customers.size()));
            int userId = staffUsers.get(random.nextInt(staffUsers.size()));

            long outboundId = nextOutboundId++;
            outPs.setLong(1, outboundId);
            outPs.setLong(2, lotId);
            outPs.setInt(3, customerId);
            outPs.setInt(4, amt);
            outPs.setObject(5, d);
            outPs.setInt(6, userId);
            outPs.addBatch();

            counts[1]++;
            outCountToday++;
            outRemaining -= amt;
        }

        return outCountToday;
    }

    private static long insertHistoricalLot(PreparedStatement lotPs, int itemId, int amt, LocalDate d, int shelfLifeDays) throws SQLException {

        String unit = itemUnit.get(itemId);
        List<Integer> zones = zonesByUnit.get(unit);
        int zoneId = zones.get(random.nextInt(zones.size()));
        int supplierId = suppliers.get(random.nextInt(suppliers.size()));
        int userId = staffUsers.get(random.nextInt(staffUsers.size()));

        long lotId = nextLotId++;

        lotPs.setLong(1, lotId);
        lotPs.setInt(2, itemId);
        lotPs.setInt(3, zoneId);
        lotPs.setInt(4, supplierId);
        lotPs.setInt(5, amt);
        lotPs.setObject(6, d);
        if (shelfLifeDays >= 0) {
            lotPs.setObject(7, d.plusDays(shelfLifeDays));
        } else {
            lotPs.setNull(7, Types.DATE);
        }
        lotPs.setInt(8, userId);
        lotPs.addBatch();

        return lotId;
    }


    /* ============================================================
       반품 / 폐기 (기존과 동일한 로직)
       ============================================================ */
    private static void doReturnDisposal(LocalDate d, int outCountToday, PreparedStatement retPs, int[] counts) throws SQLException {

        String[] returnReasons = { "고객반품", "공급처반품", "단순변심" };
        String[] disposalReasons = { "파손", "유통기한만료", "품질불량" };

        int retCount = Math.max(1, Math.round(outCountToday * (10f / 45f)));

        for (int i = 0; i < retCount; i++) {
            long lotId = allLotIds.get(random.nextInt(allLotIds.size()));
            int userId = staffUsers.get(random.nextInt(staffUsers.size()));
            int qty = 1 + random.nextInt(5);

            boolean isReturn = random.nextBoolean();
            String type = isReturn ? "반품" : "폐기";
            String reason = isReturn
                    ? returnReasons[random.nextInt(returnReasons.length)]
                    : disposalReasons[random.nextInt(disposalReasons.length)];

            retPs.setLong(1, nextReturnId++);
            retPs.setLong(2, lotId);
            retPs.setString(3, type);
            retPs.setString(4, reason);
            retPs.setInt(5, qty);
            retPs.setInt(6, userId);
            retPs.setObject(7, d);
            retPs.addBatch();

            counts[2]++;
        }
    }


    /* ============================================================
       재고부족 알림 -> (일부는) 자동 발주 승인 -> 자동 입고 -> 자동입고 알림
       실제 ApprovalService.executeOutboundApproval()이 하는 흐름과 같은 순서로 남깁니다.
       ============================================================ */
    private static void doShortageChain(LocalDate d, PreparedStatement lotPs, PreparedStatement alertPs, PreparedStatement approvalPs, int[] counts) throws SQLException {

        int shortageCount = 3 + random.nextInt(4); // 하루 3~6건

        for (int i = 0; i < shortageCount; i++) {

            int[] item = items.get(random.nextInt(items.size()));
            int itemId = item[0];
            Integer minQty = itemThresholdMin.get(itemId);
            if (minQty == null) minQty = 10;
            int currentQty = Math.max(0, minQty - 1 - random.nextInt(minQty));

            boolean fromDisposal = random.nextBoolean();
            String msg = "품목(itemId=" + itemId + ") 재고가 threshold_min(" + minQty + ") 미만입니다 (현재 " + currentQty
                    + (fromDisposal ? ", 폐기로 인한 감소)" : ")");

            LocalDateTime createdAt = randomTimeOn(d);
            boolean resolveViaAutoReplenish = random.nextInt(100) < 60; // 60%는 자동으로 채워지는 흐름까지 재현

            long alertId = insertAlert(alertPs, itemId, "재고부족", msg, resolveViaAutoReplenish, createdAt, counts);

            if (!resolveViaAutoReplenish) {
                continue;
            }

            // 자동 발주 승인 (사람 개입 없이 승인 상태로 바로 기록 - 시스템 자동 제안이라 requested_by는 NULL)
            int shortfall = minQty - currentQty;
            int[] recentLike = items.get(random.nextInt(items.size())); // 그냥 여유분 계산용
            int extra = 20 + random.nextInt(80);
            int autoQty = shortfall + extra;

            LocalDateTime approvedAt = createdAt.plusMinutes(1 + random.nextInt(30));
            int approvedByUser = staffUsers.get(random.nextInt(staffUsers.size()));

            long approvalId = nextApprovalId++;
            approvalPs.setLong(1, approvalId);
            approvalPs.setInt(2, itemId);
            approvalPs.setLong(3, alertId);
            approvalPs.setString(4, "발주");
            approvalPs.setInt(5, autoQty);
            approvalPs.setNull(6, Types.BIGINT); // 발주는 partnerId 없어도 자동실행 시 기존 로트를 참고
            approvalPs.setString(7, "승인");
            approvalPs.setNull(8, Types.BIGINT); // 시스템 자동 제안
            approvalPs.setInt(9, approvedByUser);
            approvalPs.setTimestamp(10, Timestamp.valueOf(createdAt));
            approvalPs.setTimestamp(11, Timestamp.valueOf(approvedAt));
            approvalPs.setInt(12, autoQty);
            approvalPs.addBatch();
            counts[4]++;

            // 그 승인이 실제로 만든 입고 로트
            long newLotId = insertHistoricalLot(lotPs, itemId, autoQty, approvedAt.toLocalDate(), item[1]);
            allLotIds.add(newLotId);
            counts[0]++;

            String inMsg = "품목(itemId=" + itemId + ") 출고 부족분을 승인 없이 자동으로 입고 처리했습니다 ("
                    + autoQty + "개, 그중 여유분 " + extra + "개, 로트 lotId=" + newLotId + ")";
            insertAlert(alertPs, itemId, "자동입고", inMsg, true, approvedAt, counts);
        }
    }


    /* ============================================================
       재고초과 알림 (InboundService와 같은 문구)
       ============================================================ */
    private static void doOverstockAlerts(LocalDate d, PreparedStatement alertPs, int[] counts) throws SQLException {

        int count = random.nextInt(3); // 하루 0~2건

        for (int i = 0; i < count; i++) {
            int[] item = items.get(random.nextInt(items.size()));
            int itemId = item[0];
            Integer maxQty = itemCapacityMax.get(itemId);
            if (maxQty == null) maxQty = 100;

            String msg = "품목(itemId=" + itemId + ") 재고가 capacity_max(" + maxQty + ")를 초과했습니다";
            insertAlert(alertPs, itemId, "재고초과", msg, random.nextBoolean(), randomTimeOn(d), counts);
        }
    }


    /* ============================================================
       이상출고 알림 -> 출고 승인요청 (ApprovalService.create()와 같은 흐름)
       ============================================================ */
    private static void doAbnormalOutboundChain(LocalDate d, PreparedStatement alertPs, PreparedStatement approvalPs, int[] counts) throws SQLException {

        int count = random.nextInt(3); // 하루 0~2건

        for (int i = 0; i < count; i++) {
            int[] item = items.get(random.nextInt(items.size()));
            int itemId = item[0];
            int totalStock = 10 + random.nextInt(200);
            int requestedQty = totalStock + 20 + random.nextInt(200);

            LocalDateTime createdAt = randomTimeOn(d);
            String msg = "품목(itemId=" + itemId + ") 출고 요청 수량(" + requestedQty + ")이 현재 재고(" + totalStock + ")보다 많습니다";

            long alertId = insertAlert(alertPs, itemId, "이상출고", msg, true, createdAt, counts);

            int customerId = customers.get(random.nextInt(customers.size()));
            int requestedByUser = staffUsers.get(random.nextInt(staffUsers.size()));

            String[] statuses = { "대기", "승인", "반려" };
            String status = statuses[random.nextInt(statuses.length)];

            LocalDateTime approvedAt = null;
            Integer approvedByUser = null;
            Integer fulfilledQty = null;

            if (!status.equals("대기")) {
                approvedAt = createdAt.plusHours(1 + random.nextInt(48));
                approvedByUser = staffUsers.get(random.nextInt(staffUsers.size()));
                fulfilledQty = status.equals("승인") ? Math.min(requestedQty, totalStock + random.nextInt(50)) : 0;
            }

            long approvalId = nextApprovalId++;
            approvalPs.setLong(1, approvalId);
            approvalPs.setInt(2, itemId);
            approvalPs.setLong(3, alertId);
            approvalPs.setString(4, "출고");
            approvalPs.setInt(5, requestedQty);
            approvalPs.setInt(6, customerId);
            approvalPs.setString(7, status);
            approvalPs.setInt(8, requestedByUser);
            if (approvedByUser != null) approvalPs.setInt(9, approvedByUser); else approvalPs.setNull(9, Types.BIGINT);
            approvalPs.setTimestamp(10, Timestamp.valueOf(createdAt));
            if (approvedAt != null) approvalPs.setTimestamp(11, Timestamp.valueOf(approvedAt)); else approvalPs.setNull(11, Types.TIMESTAMP);
            if (fulfilledQty != null) approvalPs.setInt(12, fulfilledQty); else approvalPs.setNull(12, Types.INTEGER);
            approvalPs.addBatch();
            counts[4]++;
        }
    }


    /* ============================================================
       자동실행실패 알림 (가끔 - 참고할 로트가 없어서 자동입고가 실패한 경우)
       ============================================================ */
    private static void doAutoFailAlerts(LocalDate d, PreparedStatement alertPs, int[] counts) throws SQLException {

        if (random.nextInt(100) >= 15) { // 하루 15% 확률로만 발생
            return;
        }

        int[] item = items.get(random.nextInt(items.size()));
        int itemId = item[0];
        int shortfall = 5 + random.nextInt(50);

        String msg = "품목(itemId=" + itemId + ") 출고 부족분(" + shortfall + "개) 자동 입고 실패 — 참고할 기존 로트가 없습니다. 입고 화면에서 직접 처리해 주세요.";
        insertAlert(alertPs, itemId, "자동실행실패", msg, random.nextBoolean(), randomTimeOn(d), counts);
    }


    /* ============================================================
       창고정리추천 알림 (WarehouseConsolidationService와 같은 문구)
       ============================================================ */
    private static void doConsolidationAlerts(LocalDate d, PreparedStatement alertPs, int[] counts) throws SQLException {

        int count = random.nextInt(3); // 하루 0~2건

        for (int i = 0; i < count; i++) {
            int[] item = items.get(random.nextInt(items.size()));
            int itemId = item[0];
            String unit = itemUnit.get(itemId);
            List<Integer> zones = zonesByUnit.get(unit);
            if (zones.size() < 2) continue;

            int fromZone = zones.get(random.nextInt(zones.size()));
            int toZone;
            do {
                toZone = zones.get(random.nextInt(zones.size()));
            } while (toZone == fromZone);

            int qty = 5 + random.nextInt(50);
            int occupancy = 10 + random.nextInt(40);

            String fromLabel = warehouseLabel.get(zoneWarehouseId.get(fromZone)) + " " + unit;
            String toLabel = warehouseLabel.get(zoneWarehouseId.get(toZone)) + " " + unit;

            String msg = "품목(itemId=" + itemId + ") 재고가 여러 구역에 분산되어 있습니다. "
                    + fromLabel + "[zoneId=" + fromZone + "](수량 " + qty + ", 점유율 " + occupancy + "%)를 "
                    + toLabel + "[zoneId=" + toZone + "]로 합치는 걸 추천합니다";

            insertAlert(alertPs, itemId, "창고정리추천", msg, random.nextBoolean(), randomTimeOn(d), counts);
        }
    }


    /* ============================================================
       창고 간 재고 이동 이력
       ============================================================ */
    private static void doTransfers(LocalDate d, PreparedStatement transferPs, int[] counts) throws SQLException {

        int count = 5 + random.nextInt(8); // 하루 5~12건

        for (int i = 0; i < count; i++) {
            long lotId = allLotIds.get(random.nextInt(allLotIds.size()));
            int[] item = items.get(random.nextInt(items.size()));
            String unit = itemUnit.get(item[0]);
            List<Integer> zones = zonesByUnit.get(unit);
            if (zones.size() < 2) continue;

            int fromZone = zones.get(random.nextInt(zones.size()));
            int toZone;
            do {
                toZone = zones.get(random.nextInt(zones.size()));
            } while (toZone == fromZone);

            int qty = 1 + random.nextInt(50);
            int handler = staffUsers.get(random.nextInt(staffUsers.size()));

            transferPs.setLong(1, nextTransferId++);
            transferPs.setLong(2, lotId);
            transferPs.setInt(3, fromZone);
            transferPs.setInt(4, toZone);
            transferPs.setInt(5, qty);
            transferPs.setInt(6, handler);
            transferPs.setTimestamp(7, Timestamp.valueOf(randomTimeOn(d)));
            transferPs.addBatch();

            counts[5]++;
        }
    }


    /* ============================================================
       감사로그 (관리자 직접수정/삭제 이력) - AuditLogService.toJson()과 같은 형식
       ============================================================ */
    private static void doChangeLogs(LocalDate d, PreparedStatement logPs, int[] counts) throws SQLException {

        int count = 3 + random.nextInt(6); // 하루 3~8건

        String[] reasons = { "수량 오입력 정정", "품목 착오로 인한 정정", "실사 결과 반영", "관리자 직접 조정" };

        for (int i = 0; i < count; i++) {
            long lotId = allLotIds.get(random.nextInt(allLotIds.size()));
            int[] item = items.get(random.nextInt(items.size()));
            int changer = staffUsers.get(random.nextInt(staffUsers.size()));

            int beforeQty = random.nextInt(200);
            int afterQty = Math.max(0, beforeQty + (random.nextInt(41) - 20));
            String zoneUnit = itemUnit.get(item[0]);
            int zoneId = zonesByUnit.get(zoneUnit).get(random.nextInt(zonesByUnit.get(zoneUnit).size()));
            String inboundDate = d.minusDays(random.nextInt(30)).toString();

            String beforeJson = lotJson(lotId, item[0], zoneId, beforeQty, beforeQty, inboundDate, "NORMAL");
            String changeType = random.nextInt(100) < 85 ? "UPDATE" : "DELETE";
            String afterJson = changeType.equals("DELETE") ? null : lotJson(lotId, item[0], zoneId, afterQty, afterQty, inboundDate, "NORMAL");

            logPs.setLong(1, nextLogId++);
            logPs.setLong(2, lotId);
            logPs.setInt(3, changer);
            logPs.setString(4, changeType);
            logPs.setString(5, beforeJson);
            if (afterJson != null) logPs.setString(6, afterJson); else logPs.setNull(6, Types.LONGVARCHAR);
            logPs.setString(7, reasons[random.nextInt(reasons.length)]);
            logPs.setBoolean(8, false);
            logPs.setTimestamp(9, Timestamp.valueOf(randomTimeOn(d)));
            logPs.addBatch();

            counts[6]++;
        }
    }

    private static String lotJson(long lotId, int itemId, int zoneId, int qty, int initialQty, String inboundDate, String status) {
        return "{\"lotId\":" + lotId + ",\"itemId\":" + itemId + ",\"zoneId\":" + zoneId
                + ",\"partnerId\":" + suppliers.get(0) + ",\"quantity\":" + qty + ",\"initialQuantity\":" + initialQty
                + ",\"inboundDate\":\"" + inboundDate + "\",\"expiryDate\":null,\"status\":\"" + status + "\",\"parentLotId\":null}";
    }


    /* ============================================================
       공통 유틸
       ============================================================ */
    private static long insertAlert(PreparedStatement alertPs, int itemId, String type, String message, boolean resolved, LocalDateTime createdAt, int[] counts) throws SQLException {
        long alertId = nextAlertId++;
        alertPs.setLong(1, alertId);
        alertPs.setInt(2, itemId);
        alertPs.setString(3, type);
        alertPs.setString(4, message);
        alertPs.setBoolean(5, resolved);
        alertPs.setTimestamp(6, Timestamp.valueOf(createdAt));
        alertPs.addBatch();
        counts[3]++;
        return alertId;
    }

    private static LocalDateTime randomTimeOn(LocalDate d) {
        return d.atTime(8 + random.nextInt(11), random.nextInt(60), random.nextInt(60)); // 08:00~18:59 사이 업무시간
    }


    /* ============================================================
       참고 데이터 조회 (하드코딩 안 하고 DB에서 그때그때 읽어옵니다)
       ============================================================ */
    private static void loadReferenceData(Connection conn) throws SQLException {

        items = new ArrayList<>();
        itemUnit = new HashMap<>();
        itemThresholdMin = new HashMap<>();
        itemCapacityMax = new HashMap<>();

        String itemSql = "SELECT item_id, unit, shelf_life_days, threshold_min, capacity_max FROM ITEM WHERE is_active = TRUE";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(itemSql)) {
            while (rs.next()) {
                int itemId = rs.getInt("item_id");
                int shelf = rs.getInt("shelf_life_days");
                if (rs.wasNull()) shelf = -1;
                items.add(new int[] { itemId, shelf });
                itemUnit.put(itemId, rs.getString("unit"));
                itemThresholdMin.put(itemId, rs.getInt("threshold_min"));
                itemCapacityMax.put(itemId, rs.getInt("capacity_max"));
            }
        }

        zonesByUnit = new HashMap<>();
        zonesByUnit.put("PALLET", new ArrayList<>());
        zonesByUnit.put("BOX", new ArrayList<>());
        zonesByUnit.put("EA", new ArrayList<>());
        zoneWarehouseId = new HashMap<>();

        String zoneSql = "SELECT zone_id, warehouse_id, zone_name FROM ZONE";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(zoneSql)) {
            while (rs.next()) {
                int zoneId = rs.getInt("zone_id");
                zonesByUnit.get(rs.getString("zone_name")).add(zoneId);
                zoneWarehouseId.put(zoneId, rs.getInt("warehouse_id"));
            }
        }

        warehouseLabel = new HashMap<>();
        String whSql = "SELECT warehouse_id, name, location FROM WAREHOUSE";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(whSql)) {
            while (rs.next()) {
                warehouseLabel.put(rs.getInt("warehouse_id"), rs.getString("name") + "(" + rs.getString("location") + ")");
            }
        }

        suppliers = loadPartnerIds(conn, "SUPPLIER");
        customers = loadPartnerIds(conn, "CUSTOMER");
        staffUsers = loadStaffUserIds(conn);

        nextLotId = nextId(conn, "STOCK_LOT", "lot_id");
        nextOutboundId = nextId(conn, "OUTBOUND", "outbound_id");
        nextReturnId = nextId(conn, "RETURN_DISPOSAL", "record_id");
        nextAlertId = nextId(conn, "ALERT", "alert_id");
        nextApprovalId = nextId(conn, "APPROVAL", "approval_id");
        nextTransferId = nextId(conn, "STOCK_TRANSFER", "transfer_id");
        nextLogId = nextId(conn, "STOCK_CHANGE_LOG", "log_id");

        allLotIds = new ArrayList<>();
        String lotSql = "SELECT lot_id FROM STOCK_LOT";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(lotSql)) {
            while (rs.next()) {
                allLotIds.add(rs.getLong("lot_id"));
            }
        }
    }

    private static List<Integer> loadPartnerIds(Connection conn, String type) throws SQLException {
        List<Integer> result = new ArrayList<>();
        String sql = "SELECT partner_id FROM PARTNER WHERE type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getInt("partner_id"));
                }
            }
        }
        return result;
    }

    private static List<Integer> loadStaffUserIds(Connection conn) throws SQLException {
        List<Integer> result = new ArrayList<>();
        String sql = "SELECT user_id FROM APP_USER WHERE role = 'STAFF'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getInt("user_id"));
            }
        }
        return result;
    }

    private static long nextId(Connection conn, String table, String idColumn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(" + idColumn + "), 0) + 1 AS next_id FROM " + table;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong("next_id");
        }
    }
}
