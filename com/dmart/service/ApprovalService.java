package com.dmart.service;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ApprovalDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.PartnerDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Approval;
import com.dmart.dto.Item;
import com.dmart.dto.Partner;
import com.dmart.dto.StockLot;
import com.dmart.dto.Zone;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// API_명세.md 12번 참고.
public class ApprovalService {

    private final ApprovalDao approvalDao = new ApprovalDao();
    private final ItemDao itemDao = new ItemDao();
    private final PartnerDao partnerDao = new PartnerDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final AlertDao alertDao = new AlertDao();
    private final InboundService inboundService = new InboundService();
    private final OutboundService outboundService = new OutboundService();

    public Long create(Long itemId, Long alertId, String requestType, int requestedQty,
                        Long partnerId, Long requestedBy) throws SQLException {
        if (!"발주".equals(requestType) && !"출고".equals(requestType)) {
            throw new IllegalArgumentException("requestType은 '발주' 또는 '출고'여야 합니다: " + requestType);
        }
        if (requestedQty <= 0) {
            throw new IllegalArgumentException("requestedQty는 0보다 커야 합니다");
        }
        if ("출고".equals(requestType) && partnerId == null) {
            throw new IllegalArgumentException("requestType이 '출고'면 partnerId(고객)는 필수입니다");
        }

        return DBConnection.executeInTransactionWithResult(conn -> {
            if (itemDao.findById(conn, itemId) == null) {
                throw new IllegalArgumentException("존재하지 않는 itemId입니다: " + itemId);
            }
            if (partnerId != null) {
                Partner partner = partnerDao.findById(conn, partnerId);
                if (partner == null) {
                    throw new IllegalArgumentException("존재하지 않는 partnerId입니다: " + partnerId);
                }
                String expectedType = "발주".equals(requestType) ? "SUPPLIER" : "CUSTOMER";
                if (!expectedType.equals(partner.getType())) {
                    throw new IllegalArgumentException("partnerId=" + partnerId + "는 " + expectedType + "가 아닙니다");
                }
            }

            // 이상출고 - 요청 수량이 지금 그 품목의 총 재고보다 많으면 알림을 만들어 관리자가
            // 검토하도록 한다(11번 참고). alertId를 이미 다른 경로에서 받아온 경우(예: 재고초과
            // 알림에서 넘어온 요청)는 중복으로 새로 만들지 않는다.
            Long effectiveAlertId = alertId;
            if (effectiveAlertId == null && "출고".equals(requestType)) {
                int totalStock = stockLotDao.sumQuantityByItemId(conn, itemId);
                if (requestedQty > totalStock) {
                    Alert abnormalAlert = new Alert();
                    abnormalAlert.setItemId(itemId);
                    abnormalAlert.setAlertType("이상출고");
                    abnormalAlert.setMessage("품목(itemId=" + itemId + ") 출고 요청 수량(" + requestedQty
                            + ")이 현재 재고(" + totalStock + ")보다 많습니다");
                    abnormalAlert.setIsResolved(false);
                    effectiveAlertId = alertDao.insert(conn, abnormalAlert);
                }
            }

            Approval approval = new Approval();
            approval.setItemId(itemId);
            approval.setAlertId(effectiveAlertId);
            approval.setRequestType(requestType);
            approval.setRequestedQty(requestedQty);
            approval.setPartnerId(partnerId);
            approval.setRequestedBy(requestedBy);
            return approvalDao.insert(conn, approval);
        });
    }

    public static class DecisionResult {
        public final Long approvalId;
        public final String status;
        public final String executedService; // "inbound" | "outbound" | null(반려)
        public final Boolean executionFailed; // null(반려)
        public final String executionError;
        public final List<Long> resultLotIds; // 발주 성공 시
        public final List<Long> resultOutboundIds; // 출고 성공 시
        public final Integer requestedQty; // 출고일 때만
        public final Integer fulfilledQty; // 출고일 때만
        public final Long shortageApprovalId; // 출고일 때, 다 못 채운 만큼 자동 발주가 생겼으면 그 approvalId

        private DecisionResult(Long approvalId, String status, String executedService, Boolean executionFailed,
                                String executionError, List<Long> resultLotIds, List<Long> resultOutboundIds,
                                Integer requestedQty, Integer fulfilledQty, Long shortageApprovalId) {
            this.approvalId = approvalId;
            this.status = status;
            this.executedService = executedService;
            this.executionFailed = executionFailed;
            this.executionError = executionError;
            this.resultLotIds = resultLotIds;
            this.resultOutboundIds = resultOutboundIds;
            this.requestedQty = requestedQty;
            this.fulfilledQty = fulfilledQty;
            this.shortageApprovalId = shortageApprovalId;
        }

        static DecisionResult rejected(Long approvalId) {
            return new DecisionResult(approvalId, "반려", null, null, null,
                    Collections.emptyList(), Collections.emptyList(), null, null, null);
        }

        static DecisionResult inboundExecuted(Long approvalId, boolean failed, String error, Long lotId) {
            return new DecisionResult(approvalId, "승인", "inbound", failed, error,
                    lotId != null ? List.of(lotId) : Collections.emptyList(), Collections.emptyList(), null, null, null);
        }

        static DecisionResult outboundExecuted(Long approvalId, List<Long> outboundIds, int requestedQty,
                                                int fulfilledQty, Long shortageApprovalId) {
            return new DecisionResult(approvalId, "승인", "outbound", fulfilledQty == 0,
                    fulfilledQty == 0 ? "출고 가능한 재고가 없어 자동 출고 실행 불가" : null,
                    Collections.emptyList(), outboundIds, requestedQty, fulfilledQty, shortageApprovalId);
        }
    }

    public DecisionResult decide(Long approvalId, String status, Long approvedBy) throws SQLException {
        if (!"승인".equals(status) && !"반려".equals(status)) {
            throw new IllegalArgumentException("status는 '승인' 또는 '반려'여야 합니다: " + status);
        }

        // 1단계: 승인/반려 결정 자체를 먼저 확정해서 저장 (자동실행은 별개 단계 — 실행이 실패해도 이 결정은 유지됨)
        // findByIdForUpdate로 락을 걸어서, 같은 승인 건이 동시에 두 번 처리(이중 자동실행)되는 걸 막는다.
        Approval approval = DBConnection.executeInTransactionWithResult(conn -> {
            Approval a = approvalDao.findByIdForUpdate(conn, approvalId);
            if (a == null) {
                throw new IllegalArgumentException("존재하지 않는 approvalId입니다: " + approvalId);
            }
            if (!"대기".equals(a.getStatus())) {
                throw new IllegalStateException("이미 처리된 승인 건입니다 (status=" + a.getStatus() + ")");
            }
            a.setStatus(status);
            a.setApprovedBy(approvedBy);
            a.setApprovedAt(LocalDateTime.now());
            approvalDao.update(conn, a);

            // 이 승인 건이 이상출고 알림에서 온 거라면, 승인이든 반려든 사람이 검토해서
            // 결정한 시점에 그 알림도 같이 해결 처리한다(재고부족/재고초과처럼 재고 수치가
            // 저절로 정상화되는 종류가 아니라, "검토했다" 자체가 해결 조건이라서).
            if (a.getAlertId() != null) {
                Alert linkedAlert = alertDao.findById(conn, a.getAlertId());
                if (linkedAlert != null && "이상출고".equals(linkedAlert.getAlertType())) {
                    linkedAlert.setIsResolved(true);
                    alertDao.update(conn, linkedAlert);
                }
            }

            return a;
        });

        if ("반려".equals(status)) {
            return DecisionResult.rejected(approvalId);
        }

        // 2단계: 자동실행 — 12번 "승인 시 자동 실행 규칙"
        // request_type엔 DB CHECK 제약이 없어서(다른 status류 컬럼과 다름) 여기서 한 번 더 명시적으로 검증 —
        // create()가 지금은 유일한 생성 경로라 실제로 걸릴 일은 없지만, 방어적으로 남겨둠.
        if ("발주".equals(approval.getRequestType())) {
            return executeInboundApproval(approval, approvedBy);
        } else if ("출고".equals(approval.getRequestType())) {
            return executeOutboundApproval(approval, approvedBy);
        } else {
            throw new IllegalStateException("알 수 없는 requestType입니다: " + approval.getRequestType());
        }
    }

    // 승인 상태(1단계)는 이미 저장됐으니, 여기서 SQLException이 나든 RuntimeException이 나든
    // 둘 다 executionFailed 결과로 돌려준다 — 여기서 예외가 그대로 던져지면 컨트롤러 쪽엔 500만 보이고
    // "사실 승인은 이미 처리됐다"는 걸 클라이언트가 알 방법이 없어짐.
    private DecisionResult executeInboundApproval(Approval approval, Long approvedBy) {
        try {
            Long zoneId;
            Long partnerId = approval.getPartnerId();
            try (Connection conn = DBConnection.getConnection()) {
                StockLot recent = stockLotDao.findMostRecentNormalByItemId(conn, approval.getItemId());
                if (recent == null) {
                    updateFulfilledQty(approval.getApprovalId(), 0);
                    return DecisionResult.inboundExecuted(approval.getApprovalId(), true,
                            "참고할 기존 로트가 없어 자동 입고 실행 불가 — POST /api/inbound로 수동 처리 필요", null);
                }
                Item item = itemDao.findById(conn, approval.getItemId());
                // 원래 쓰던 구역(recent.zoneId)이 그새 꽉 찼을 수 있다 - 사람 개입 없이 같은 단위(zone_name)의
                // 다른 구역 중 자리가 있는 곳을 찾아 대신 쓴다(findZoneWithRoom 참고). 그마저 없으면
                // 예전처럼 실행 실패로 보고해서 사람이 POST /api/inbound로 직접 처리하게 한다.
                zoneId = findZoneWithRoom(conn, recent.getZoneId(), item.getUnit(), approval.getRequestedQty());
                if (zoneId == null) {
                    updateFulfilledQty(approval.getApprovalId(), 0);
                    return DecisionResult.inboundExecuted(approval.getApprovalId(), true,
                            "기존 구역(zoneId=" + recent.getZoneId() + ")도, 여유 있는 다른 구역도 없어 자동 입고 실행 불가 — POST /api/inbound로 수동 처리 필요", null);
                }
                if (partnerId == null) {
                    partnerId = recent.getPartnerId();
                }
            }

            InboundService.InboundResult result = inboundService.inbound(
                    approval.getItemId(), zoneId, partnerId, approval.getRequestedQty(), LocalDate.now(), approvedBy);
            updateFulfilledQty(approval.getApprovalId(), approval.getRequestedQty());
            return DecisionResult.inboundExecuted(approval.getApprovalId(), false, null, result.lotId);
        } catch (SQLException | RuntimeException e) {
            updateFulfilledQty(approval.getApprovalId(), 0);
            return DecisionResult.inboundExecuted(approval.getApprovalId(), true, e.getMessage(), null);
        }
    }

    private DecisionResult executeOutboundApproval(Approval approval, Long approvedBy) {
        int requestedQty = approval.getRequestedQty();

        // 1) 먼저 부족한지만 확인해서, 부족하면 출고를 시도하기 "전에" 미리 채워 넣어 본다.
        //    사람이 이미 이 출고 승인요청 자체를 승인한 것을 "부족한 만큼은 채워서라도 처리해도
        //    된다"는 의사표시로 보고, 부족분 발주는 사람의 추가 승인 없이 바로 실행한다.
        //    이 시도가 실패해도(참고할 로트가 없음 등) 아래 2)에서 실제로 있는 만큼은 그대로
        //    출고되니 손해가 없다 — 순서를 바꿔서 "출고 → 부족분 입고 → 재출고"처럼 두 번
        //    나눠 하지 않고, 최종적으로 딱 한 번만 출고하면 되게 만든 것.
        Long shortageApprovalId = null;
        try (Connection conn = DBConnection.getConnection()) {
            int totalStock = stockLotDao.sumQuantityByItemId(conn, approval.getItemId());
            if (requestedQty > totalStock) {
                shortageApprovalId = tryAutoReplenish(approval.getItemId(), requestedQty - totalStock, approvedBy);
            }
        } catch (SQLException e) {
            System.err.println("승인(approvalId=" + approval.getApprovalId() + ") 재고 확인 실패: " + e.getMessage());
        }

        // 2) 이 시점에 실제로 있는 만큼만 정직하게, 딱 한 번 출고한다
        //    (1번이 성공했으면 방금 들어온 만큼도 자연히 포함됨).
        List<Long> outboundIds = new ArrayList<>();
        int remaining = requestedQty;
        int fulfilled = 0;
        try {
            OutboundService.RecommendResult recommend = outboundService.recommend(
                    approval.getItemId(), requestedQty, "fefo");

            for (StockLot lot : recommend.lots) {
                if (remaining <= 0) {
                    break;
                }
                int take = Math.min(remaining, lot.getQuantity());
                try {
                    OutboundService.OutboundResult result = outboundService.outbound(
                            lot.getLotId(), approval.getPartnerId(), take, LocalDate.now(), approvedBy);
                    outboundIds.add(result.outboundId);
                    fulfilled += take;
                    remaining -= take;
                } catch (SQLException | RuntimeException e) {
                    // 이 로트 처리 중 문제(그 사이 다른 요청이 재고를 먼저 가져갔을 수도 있음) - 다음 로트로 계속 시도.
                    // 원인 파악할 수 있게 남겨둠(운영 로그로 대체 가능한 최소 기록).
                    System.err.println("승인(approvalId=" + approval.getApprovalId() + ") 자동 출고 중 lotId="
                            + lot.getLotId() + " 처리 실패: " + e.getMessage());
                }
            }
        } catch (SQLException | RuntimeException e) {
            System.err.println("승인(approvalId=" + approval.getApprovalId() + ") 자동 출고 추천 조회 실패: " + e.getMessage());
        }

        updateFulfilledQty(approval.getApprovalId(), fulfilled);
        return DecisionResult.outboundExecuted(approval.getApprovalId(), outboundIds, requestedQty,
                fulfilled, shortageApprovalId);
    }

    // 부족분을 사람 승인 없이 자동으로 입고 처리해 본다(최선을 다해 보는 것뿐이라 실패해도
    // 예외를 던지지 않고 null을 돌려준다 - 호출하는 쪽은 실패하든 성공하든 그다음 출고를
    // 실제 재고 기준으로 그대로 진행하면 되므로 결과를 몰라도 무방함).
    private Long tryAutoReplenish(Long itemId, int shortfall, Long approvedBy) {
        StockLot recentLot;
        Item item;
        try (Connection conn = DBConnection.getConnection()) {
            recentLot = stockLotDao.findMostRecentNormalByItemId(conn, itemId);
            item = itemDao.findById(conn, itemId);
        } catch (SQLException e) {
            System.err.println("품목(itemId=" + itemId + ") 참고 로트 조회 실패: " + e.getMessage());
            return null;
        }

        if (recentLot == null) {
            // 참고할 로트가 없어 어느 구역/거래처로 자동 입고할지 정할 수 없는 경우 -
            // 알림만 남기고 사람이 POST /api/inbound로 직접 처리하게 한다.
            createAlert(itemId, "자동실행실패",
                    "품목(itemId=" + itemId + ") 출고 부족분(" + shortfall
                            + "개) 자동 입고 실패 — 참고할 기존 로트가 없습니다. 입고 화면에서 직접 처리해 주세요.");
            return null;
        }

        // 부족분만 딱 채우면 금방 또 모자라질 수 있어서, 최근에 이 품목을 한 번에 얼마나
        // 들여왔는지(가장 최근 정상 로트의 initial_quantity)를 여유분으로 더 얹어 입고한다.
        int extra = recentLot.getInitialQuantity() != null ? recentLot.getInitialQuantity() : 0;
        int autoQty = shortfall + extra;

        // 원래 쓰던 구역이 그새 꽉 찼을 수 있다 - 같은 단위(zone_name)의 다른 구역 중 자리가
        // 있는 곳을 찾아 대신 쓴다. 그마저 없으면 사람이 직접 처리하도록 알림만 남긴다.
        Long zoneId;
        try (Connection conn = DBConnection.getConnection()) {
            zoneId = findZoneWithRoom(conn, recentLot.getZoneId(), item.getUnit(), autoQty);
        } catch (SQLException e) {
            System.err.println("품목(itemId=" + itemId + ") 구역 조회 실패: " + e.getMessage());
            zoneId = null;
        }
        if (zoneId == null) {
            createAlert(itemId, "자동실행실패",
                    "품목(itemId=" + itemId + ") 출고 부족분(" + shortfall
                            + "개) 자동 입고 실패 — 기존 구역도, 여유 있는 다른 구역도 없습니다. 입고 화면에서 직접 처리해 주세요.");
            return null;
        }

        Long shortageApprovalId = null;
        try {
            shortageApprovalId = createAutoApprovedShortageApproval(itemId, autoQty, approvedBy);

            InboundService.InboundResult inboundResult = inboundService.inbound(
                    itemId, zoneId, recentLot.getPartnerId(), autoQty, LocalDate.now(), approvedBy);

            String zoneNote = zoneId.equals(recentLot.getZoneId()) ? "" : ", 기존 구역이 꽉 차서 zoneId=" + zoneId + "로 대신 입고";
            createAlert(itemId, "자동입고",
                    "품목(itemId=" + itemId + ") 출고 부족분을 승인 없이 자동으로 입고 처리했습니다 ("
                            + autoQty + "개, 그중 여유분 " + extra + "개, 로트 lotId=" + inboundResult.lotId + zoneNote + ")");

            updateFulfilledQty(shortageApprovalId, autoQty);
            return shortageApprovalId;
        } catch (SQLException | RuntimeException e) {
            System.err.println("품목(itemId=" + itemId + ") 부족분 자동 입고 처리 실패: " + e.getMessage());
            createAlert(itemId, "자동실행실패",
                    "품목(itemId=" + itemId + ") 출고 부족분(" + shortfall
                            + "개) 자동 입고 처리 중 오류가 발생했습니다: " + e.getMessage());
            // shortageApprovalId row가 이미 만들어졌을 수도 있음(발주 기록은 승인 상태 그대로 두고
            // 실제로는 아무것도 안 들어왔다는 것만 남긴다) - null이면(insert 자체가 실패) 남길 게 없음.
            if (shortageApprovalId != null) {
                updateFulfilledQty(shortageApprovalId, 0);
            }
            return null;
        }
    }

    // 부족분을 사람 승인 없이 바로 "승인" 상태로 만들어 기록만 남긴다(alertId 없음 - 이 승인 건
    // 자체가 원인이지 알림에서 온 게 아니므로). approvedBy는 이 자동 처리를 촉발한, 원래 출고
    // 승인요청을 승인한 사람 그대로 남긴다.
    private Long createAutoApprovedShortageApproval(Long itemId, int qty, Long approvedBy) throws SQLException {
        return DBConnection.executeInTransactionWithResult(conn -> {
            Approval shortage = new Approval();
            shortage.setItemId(itemId);
            shortage.setAlertId(null);
            shortage.setRequestType("발주");
            shortage.setRequestedQty(qty);
            shortage.setRequestedBy(null);
            shortage.setStatus("승인");
            shortage.setApprovedBy(approvedBy);
            shortage.setApprovedAt(LocalDateTime.now());
            return approvalDao.insert(conn, shortage);
        });
    }

    // 자동 실행(발주 승인 실행/출고 부족분 자동 보충)이 쓰려던 구역(preferredZoneId)이 꽉 찼을 때,
    // 같은 단위(zone_name)의 다른 구역 중 지금 quantity만큼 넣을 자리가 있는 곳을 찾는다.
    // preferredZoneId에 그대로 자리가 있으면 그 구역을 그대로 쓰고(원래 쓰던 구역 유지),
    // 없을 때만 대안을 찾는다 - 여러 후보 중에는 지금 가장 여유가 많이 남은 구역을 골라
    // 한 구역에만 몰리지 않게 한다. 그마저도 없으면 null(호출하는 쪽이 "자동실행실패" 알림으로
    // 사람에게 넘긴다 - InboundService.inbound()의 구역 용량 체크와 같은 기준).
    private Long findZoneWithRoom(Connection conn, Long preferredZoneId, String unit, int quantity) throws SQLException {
        Zone preferred = zoneDao.findById(conn, preferredZoneId);
        if (preferred != null && zoneHasRoom(conn, preferred, quantity)) {
            return preferredZoneId;
        }

        Long bestZoneId = null;
        int bestRoom = -1;
        for (Zone zone : zoneDao.findAll(conn)) {
            if (!unit.equals(zone.getZoneName()) || zone.getZoneId().equals(preferredZoneId)) {
                continue;
            }
            if (zone.getCapacity() == null) {
                return zone.getZoneId(); // 용량 제한 자체가 없는 구역이면 더 볼 것 없이 바로 확정
            }
            int room = zone.getCapacity() - stockLotDao.sumQuantityByZoneId(conn, zone.getZoneId());
            if (room >= quantity && room > bestRoom) {
                bestZoneId = zone.getZoneId();
                bestRoom = room;
            }
        }
        return bestZoneId;
    }

    private boolean zoneHasRoom(Connection conn, Zone zone, int quantity) throws SQLException {
        if (zone.getCapacity() == null) {
            return true;
        }
        return stockLotDao.sumQuantityByZoneId(conn, zone.getZoneId()) + quantity <= zone.getCapacity();
    }

    // 승인 건이 실제로 얼마나 처리됐는지 기록한다(실패해도 전체 흐름을 막을 이유가 없어 예외를 던지지 않고 로그만 남김).
    private void updateFulfilledQty(Long approvalId, Integer fulfilledQty) {
        try (Connection conn = DBConnection.getConnection()) {
            approvalDao.updateFulfilledQty(conn, approvalId, fulfilledQty);
        } catch (SQLException e) {
            System.err.println("승인(approvalId=" + approvalId + ") 처리 수량 기록 실패: " + e.getMessage());
        }
    }

    // 정보 전달용 알림 하나를 남긴다(실패해도 전체 흐름을 막을 이유가 없어 예외를 던지지 않고 로그만 남김).
    private void createAlert(Long itemId, String alertType, String message) {
        try (Connection conn = DBConnection.getConnection()) {
            Alert alert = new Alert();
            alert.setItemId(itemId);
            alert.setAlertType(alertType);
            alert.setMessage(message);
            alert.setIsResolved(false);
            alertDao.insert(conn, alert);
        } catch (SQLException e) {
            System.err.println("알림(" + alertType + ") 생성 실패: " + e.getMessage());
        }
    }
}
