package com.dmart.service;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ApprovalDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.ReturnDisposalDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Approval;
import com.dmart.dto.Item;
import com.dmart.dto.ReturnDisposal;
import com.dmart.dto.StockLot;

import java.sql.SQLException;
import java.time.LocalDate;

// API_명세.md 9번 참고. 8번(TransferService)과 같은 전체/분할 패턴.
public class ReturnDisposalService {

    private final ItemDao itemDao = new ItemDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final ReturnDisposalDao returnDisposalDao = new ReturnDisposalDao();
    private final AlertDao alertDao = new AlertDao();
    private final ApprovalDao approvalDao = new ApprovalDao();
    private final AuditLogService auditLogService = new AuditLogService();
    private final AlertResolutionService alertResolutionService = new AlertResolutionService();

    public static class ReturnDisposalResult {
        public final Long recordId;
        public final boolean splitOccurred;
        public final Long disposedLotId;
        public final boolean alertCreated;
        public final Long approvalId;

        public ReturnDisposalResult(Long recordId, boolean splitOccurred, Long disposedLotId,
                                     boolean alertCreated, Long approvalId) {
            this.recordId = recordId;
            this.splitOccurred = splitOccurred;
            this.disposedLotId = disposedLotId;
            this.alertCreated = alertCreated;
            this.approvalId = approvalId;
        }
    }

    public ReturnDisposalResult process(Long lotId, String type, String reason, int quantity,
                                         Long processedBy, LocalDate processedDate) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 0보다 커야 합니다");
        }
        String targetStatus;
        if ("반품".equals(type)) {
            targetStatus = "RETURNED";
        } else if ("폐기".equals(type)) {
            targetStatus = "DISPOSED";
        } else {
            throw new IllegalArgumentException("type은 '반품' 또는 '폐기'여야 합니다: " + type);
        }

        return DBConnection.executeInTransactionWithResult(conn -> {
            StockLot lot = stockLotDao.findByIdForUpdate(conn, lotId);
            if (lot == null) {
                throw new IllegalArgumentException("존재하지 않는 lotId입니다: " + lotId);
            }
            if (!"NORMAL".equals(lot.getStatus())) {
                throw new IllegalStateException("로트(lotId=" + lotId + ")는 NORMAL 상태가 아니라 반품/폐기할 수 없습니다 (status=" + lot.getStatus() + ")");
            }
            if (quantity > lot.getQuantity()) {
                throw new IllegalStateException(
                        "로트(lotId=" + lotId + ") 남은 수량(" + lot.getQuantity() + ")보다 많은 수량(" + quantity + ")을 반품/폐기할 수 없습니다");
            }

            ReturnDisposal record = new ReturnDisposal();
            record.setLotId(lotId);
            record.setType(type);
            record.setReason(reason);
            record.setQuantity(quantity);
            record.setProcessedBy(processedBy);
            record.setProcessedDate(processedDate);
            Long recordId = returnDisposalDao.insert(conn, record);

            StockLot before = lot.copy();
            boolean splitOccurred;
            Long disposedLotId;

            if (quantity == lot.getQuantity()) {
                // 전체 반품/폐기: 원본 로트 자체를 대상 상태로 변경
                lot.setStatus(targetStatus);
                stockLotDao.update(conn, lot);
                auditLogService.logUpdate(conn, before, lot, processedBy, type);
                splitOccurred = false;
                disposedLotId = lot.getLotId();
            } else {
                // 부분 반품/폐기: 원본은 수량만 차감(NORMAL 유지), 버려지는 부분만 새 로트로 분할해 대상 상태로 설정
                lot.setQuantity(lot.getQuantity() - quantity);
                stockLotDao.update(conn, lot);
                auditLogService.logUpdate(conn, before, lot, processedBy, type);

                StockLot newLot = new StockLot();
                newLot.setItemId(lot.getItemId());
                newLot.setZoneId(lot.getZoneId());
                newLot.setPartnerId(lot.getPartnerId());
                newLot.setQuantity(quantity);
                newLot.setInboundDate(lot.getInboundDate());
                newLot.setExpiryDate(lot.getExpiryDate());
                newLot.setStatus(targetStatus);
                newLot.setCreatedBy(processedBy);
                newLot.setParentLotId(lot.getLotId());
                disposedLotId = stockLotDao.insert(conn, newLot);
                splitOccurred = true;
            }

            // 재고부족 체크 + 발주 승인 자동생성 — OutboundService(7.1)와 동일한 이유:
            // 재고가 줄어드는 원인이 판매(출고)든 폐기/반품이든, 결과적으로 부족해지는 건 똑같음.
            boolean alertCreated = false;
            Long approvalId = null;
            Item item = itemDao.findById(conn, lot.getItemId());
            if (item != null && item.getThresholdMin() != null) {
                int totalAfter = stockLotDao.sumQuantityByItemId(conn, lot.getItemId());
                if (totalAfter < item.getThresholdMin()) {
                    Alert shortageAlert = new Alert();
                    shortageAlert.setItemId(lot.getItemId());
                    shortageAlert.setAlertType("재고부족");
                    shortageAlert.setMessage("품목(itemId=" + lot.getItemId() + ") 재고가 threshold_min("
                            + item.getThresholdMin() + ") 미만입니다 (현재 " + totalAfter + ", " + type + "로 인한 감소)");
                    shortageAlert.setIsResolved(false);
                    Long alertId = alertDao.insert(conn, shortageAlert);
                    alertCreated = true;

                    Approval approval = new Approval();
                    approval.setItemId(lot.getItemId());
                    approval.setAlertId(alertId);
                    approval.setRequestType("발주");
                    approval.setRequestedQty(item.getThresholdMin() - totalAfter);
                    approval.setRequestedBy(null);
                    approvalId = approvalDao.insert(conn, approval);
                }
            }

            // 11번 자동 해결 규칙 — 이번 반품/폐기로 재고초과가 해소됐을 수 있음
            alertResolutionService.reevaluate(conn, lot.getItemId());

            return new ReturnDisposalResult(recordId, splitOccurred, disposedLotId, alertCreated, approvalId);
        });
    }
}
