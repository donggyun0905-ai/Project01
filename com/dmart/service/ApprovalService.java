package com.dmart.service;

import com.dmart.dao.ApprovalDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.PartnerDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Approval;
import com.dmart.dto.Partner;
import com.dmart.dto.StockLot;

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
            Approval approval = new Approval();
            approval.setItemId(itemId);
            approval.setAlertId(alertId);
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

        private DecisionResult(Long approvalId, String status, String executedService, Boolean executionFailed,
                                String executionError, List<Long> resultLotIds, List<Long> resultOutboundIds,
                                Integer requestedQty, Integer fulfilledQty) {
            this.approvalId = approvalId;
            this.status = status;
            this.executedService = executedService;
            this.executionFailed = executionFailed;
            this.executionError = executionError;
            this.resultLotIds = resultLotIds;
            this.resultOutboundIds = resultOutboundIds;
            this.requestedQty = requestedQty;
            this.fulfilledQty = fulfilledQty;
        }

        static DecisionResult rejected(Long approvalId) {
            return new DecisionResult(approvalId, "반려", null, null, null,
                    Collections.emptyList(), Collections.emptyList(), null, null);
        }

        static DecisionResult inboundExecuted(Long approvalId, boolean failed, String error, Long lotId) {
            return new DecisionResult(approvalId, "승인", "inbound", failed, error,
                    lotId != null ? List.of(lotId) : Collections.emptyList(), Collections.emptyList(), null, null);
        }

        static DecisionResult outboundExecuted(Long approvalId, List<Long> outboundIds, int requestedQty, int fulfilledQty) {
            return new DecisionResult(approvalId, "승인", "outbound", fulfilledQty == 0,
                    fulfilledQty == 0 ? "출고 가능한 재고가 없어 자동 출고 실행 불가" : null,
                    Collections.emptyList(), outboundIds, requestedQty, fulfilledQty);
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
                    return DecisionResult.inboundExecuted(approval.getApprovalId(), true,
                            "참고할 기존 로트가 없어 자동 입고 실행 불가 — POST /api/inbound로 수동 처리 필요", null);
                }
                zoneId = recent.getZoneId();
                if (partnerId == null) {
                    partnerId = recent.getPartnerId();
                }
            }

            InboundService.InboundResult result = inboundService.inbound(
                    approval.getItemId(), zoneId, partnerId, approval.getRequestedQty(), LocalDate.now(), approvedBy);
            return DecisionResult.inboundExecuted(approval.getApprovalId(), false, null, result.lotId);
        } catch (SQLException | RuntimeException e) {
            return DecisionResult.inboundExecuted(approval.getApprovalId(), true, e.getMessage(), null);
        }
    }

    private DecisionResult executeOutboundApproval(Approval approval, Long approvedBy) {
        List<Long> outboundIds = new ArrayList<>();
        int remaining = approval.getRequestedQty();
        int fulfilled = 0;
        try {
            OutboundService.RecommendResult recommend = outboundService.recommend(
                    approval.getItemId(), approval.getRequestedQty(), "fefo");

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

        return DecisionResult.outboundExecuted(approval.getApprovalId(), outboundIds, approval.getRequestedQty(), fulfilled);
    }
}
