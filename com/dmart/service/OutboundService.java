package com.dmart.service;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ApprovalDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.OutboundDao;
import com.dmart.dao.PartnerDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Approval;
import com.dmart.dto.Item;
import com.dmart.dto.Outbound;
import com.dmart.dto.Partner;
import com.dmart.dto.StockLot;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// API_명세.md 7번 참고.
public class OutboundService {

    private static final double ABNORMAL_OUTBOUND_MULTIPLIER = 3.0; // 임시값 — 실측 후 조정 (11번 참고)

    private final ItemDao itemDao = new ItemDao();
    private final PartnerDao partnerDao = new PartnerDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final OutboundDao outboundDao = new OutboundDao();
    private final AlertDao alertDao = new AlertDao();
    private final ApprovalDao approvalDao = new ApprovalDao();
    private final AuditLogService auditLogService = new AuditLogService();
    private final AlertResolutionService alertResolutionService = new AlertResolutionService();

    public static class OutboundResult {
        public final Long outboundId;
        public final int remainingQuantity;
        public final boolean alertCreated;
        public final Long approvalId;

        public OutboundResult(Long outboundId, int remainingQuantity, boolean alertCreated, Long approvalId) {
            this.outboundId = outboundId;
            this.remainingQuantity = remainingQuantity;
            this.alertCreated = alertCreated;
            this.approvalId = approvalId;
        }
    }

    public OutboundResult outbound(Long lotId, Long partnerId, int quantity,
                                    LocalDate outboundDate, Long createdBy) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 0보다 커야 합니다");
        }

        return DBConnection.executeInTransactionWithResult(conn -> {
            // 동시 출고 레이스 컨디션 방지 — 7.1 구현 노트
            StockLot lot = stockLotDao.findByIdForUpdate(conn, lotId);
            if (lot == null) {
                throw new IllegalArgumentException("존재하지 않는 lotId입니다: " + lotId);
            }
            if (!"NORMAL".equals(lot.getStatus())) {
                throw new IllegalStateException("로트(lotId=" + lotId + ")는 NORMAL 상태가 아니라 출고할 수 없습니다 (status=" + lot.getStatus() + ")");
            }
            Partner partner = partnerDao.findById(conn, partnerId);
            if (partner == null) {
                throw new IllegalArgumentException("존재하지 않는 partnerId입니다: " + partnerId);
            }
            if (!"CUSTOMER".equals(partner.getType())) {
                throw new IllegalArgumentException("partnerId=" + partnerId + "는 고객(CUSTOMER)이 아닙니다");
            }
            if (quantity > lot.getQuantity()) {
                throw new IllegalStateException(
                        "재고 수량(" + lot.getQuantity() + ")보다 많은 수량(" + quantity + ")을 출고할 수 없습니다");
            }

            // 이상출고 체크용 평균은 이번 건이 OUTBOUND에 들어가기 "전"에 구해야 함 —
            // insert 후에 구하면 이번 건 자체가 평균에 섞여 들어가서 판정이 왜곡됨 (자기 자신을 포함해 평균을 올려버림).
            Double avgQuantity = outboundDao.averageQuantityByItemId(conn, lot.getItemId());

            StockLot before = lot.copy();
            int remaining = lot.getQuantity() - quantity;
            lot.setQuantity(remaining);
            stockLotDao.update(conn, lot);
            auditLogService.logUpdate(conn, before, lot, createdBy, "출고");

            Outbound outbound = new Outbound();
            outbound.setLotId(lotId);
            outbound.setPartnerId(partnerId);
            outbound.setQuantity(quantity);
            outbound.setOutboundDate(outboundDate);
            outbound.setCreatedBy(createdBy);
            Long outboundId = outboundDao.insert(conn, outbound);

            Item item = itemDao.findById(conn, lot.getItemId());

            // 이상출고 체크 — 요구사항 3번 "데이터 이상치 검증" (11번, 3배는 임시값)
            if (avgQuantity != null && quantity > avgQuantity * ABNORMAL_OUTBOUND_MULTIPLIER) {
                Alert abnormalAlert = new Alert();
                abnormalAlert.setItemId(lot.getItemId());
                abnormalAlert.setAlertType("이상출고");
                abnormalAlert.setMessage("품목(itemId=" + lot.getItemId() + ") 출고 수량(" + quantity
                        + ")이 평균(" + String.format("%.1f", avgQuantity) + ")의 " + ABNORMAL_OUTBOUND_MULTIPLIER + "배를 초과했습니다");
                abnormalAlert.setIsResolved(false);
                alertDao.insert(conn, abnormalAlert);
            }

            // 재고부족 체크 + 발주 승인 자동생성 — 7.1
            boolean alertCreated = false;
            Long approvalId = null;
            if (item != null && item.getThresholdMin() != null) {
                int totalAfter = stockLotDao.sumQuantityByItemId(conn, lot.getItemId());
                if (totalAfter < item.getThresholdMin()) {
                    Alert shortageAlert = new Alert();
                    shortageAlert.setItemId(lot.getItemId());
                    shortageAlert.setAlertType("재고부족");
                    shortageAlert.setMessage("품목(itemId=" + lot.getItemId() + ") 재고가 threshold_min("
                            + item.getThresholdMin() + ") 미만입니다 (현재 " + totalAfter + ")");
                    shortageAlert.setIsResolved(false);
                    Long alertId = alertDao.insert(conn, shortageAlert);
                    alertCreated = true;

                    Approval approval = new Approval();
                    approval.setItemId(lot.getItemId());
                    approval.setAlertId(alertId);
                    approval.setRequestType("발주");
                    approval.setRequestedQty(item.getThresholdMin() - totalAfter); // 임계치 복구에 필요한 최소량
                    approval.setRequestedBy(null); // 시스템 자동 제안
                    approvalId = approvalDao.insert(conn, approval);
                }
            }

            // 11번 자동 해결 규칙 — 이번 출고로 재고초과가 해소됐을 수 있음
            alertResolutionService.reevaluate(conn, lot.getItemId());

            return new OutboundResult(outboundId, remaining, alertCreated, approvalId);
        });
    }

    public static class RecommendResult {
        public final String sortBy;
        public final int requestedQuantity;
        public final boolean sufficient;
        public final List<StockLot> lots;

        public RecommendResult(String sortBy, int requestedQuantity, boolean sufficient, List<StockLot> lots) {
            this.sortBy = sortBy;
            this.requestedQuantity = requestedQuantity;
            this.sufficient = sufficient;
            this.lots = lots;
        }
    }

    // API_명세.md 7.2 — 실제 차감은 안 하는 조회 전용 로직이라 트랜잭션 없이 단순 조회.
    public RecommendResult recommend(Long itemId, int quantity, String sortBy) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 0보다 커야 합니다");
        }
        boolean fifo = "fifo".equalsIgnoreCase(sortBy);
        String effectiveSortBy = fifo ? "fifo" : "fefo";

        try (Connection conn = DBConnection.getConnection()) {
            if (itemDao.findById(conn, itemId) == null) {
                throw new IllegalArgumentException("존재하지 않는 itemId입니다: " + itemId);
            }
            List<StockLot> candidates = fifo
                    ? stockLotDao.findByItemIdOrderByInboundDate(conn, itemId)
                    : stockLotDao.findByItemIdOrderByExpiryDate(conn, itemId);

            List<StockLot> picked = new ArrayList<>();
            int remaining = quantity;
            for (StockLot lot : candidates) {
                if (!"NORMAL".equals(lot.getStatus())) {
                    continue; // 이미 반품/폐기된 로트는 후보에서 제외 — 7.2 참고
                }
                if (lot.getQuantity() <= 0) {
                    continue; // 이미 다 소진된 로트(quantity=0)는 추천에서 제외
                }
                if (remaining <= 0) {
                    break;
                }
                picked.add(lot);
                remaining -= lot.getQuantity();
            }
            boolean sufficient = remaining <= 0;
            return new RecommendResult(effectiveSortBy, quantity, sufficient, picked);
        }
    }
}
