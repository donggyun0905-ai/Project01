package com.dmart.service;

import com.dmart.dao.StockChangeLogDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.StockChangeLog;
import com.dmart.dto.StockLot;
import com.dmart.util.JsonUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

// API_명세.md 10.2~10.4 참고. 정상 업무 흐름(입고/출고/이동/반품)이 아니라 관리자가 재고 실사 오차 등을
// 직접 보정/삭제/복원하는 경로 — AuditLogService로 감사 로그를 남기는 것 외엔 다른 서비스들과 동일한 패턴.
//
// 10.3(삭제)은 실제로 STOCK_LOT row를 지우지 않고 status='DELETED'로 바꾸는 소프트 삭제다.
// 이유: STOCK_CHANGE_LOG.lot_id가 STOCK_LOT을 FK(기본 RESTRICT)로 참조하는데, "삭제했다는 사실 자체를
// 그 lot_id로 감사 로그에 남겨야" 하는 10.3 요구사항과 실제 물리 삭제가 스키마상 동시에 성립할 수 없다
// (로그를 먼저 넣으면 그 로그가 참조를 붙잡아서 삭제가 막히고, 삭제를 먼저 하면 그 다음 로그 insert가
// 이미 없는 lot_id를 참조해서 막힘). 소프트 삭제로 풀면 row가 계속 존재하므로 이 충돌 자체가 사라지고,
// 10.4 복원도 같은 lotId 그대로 되살릴 수 있어 응답 명세("lotId": 55 그대로 복원)와도 맞는다.
public class StockLotAdjustmentService {

    private final StockLotDao stockLotDao = new StockLotDao();
    private final StockChangeLogDao stockChangeLogDao = new StockChangeLogDao();
    private final AuditLogService auditLogService = new AuditLogService();
    private final AlertResolutionService alertResolutionService = new AlertResolutionService();

    public static class AdjustResult {
        public final Long lotId;
        public final Integer quantity;
        public final String status;

        public AdjustResult(Long lotId, Integer quantity, String status) {
            this.lotId = lotId;
            this.quantity = quantity;
            this.status = status;
        }
    }

    public AdjustResult adjust(Long lotId, Integer quantity, String status, String reason,
                                Long changedBy) throws SQLException {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason은 필수입니다");
        }
        if (quantity != null && quantity < 0) {
            throw new IllegalArgumentException("quantity는 0 이상이어야 합니다");
        }
        if (status != null && !"NORMAL".equals(status) && !"DISPOSED".equals(status) && !"RETURNED".equals(status)) {
            throw new IllegalArgumentException("status는 NORMAL/DISPOSED/RETURNED 중 하나여야 합니다: " + status);
        }

        return DBConnection.executeInTransactionWithResult(conn -> {
            // 동시 수정 방지 — 6·7·8·9번과 동일한 이유로 대상 row를 잠근다.
            StockLot lot = stockLotDao.findByIdForUpdate(conn, lotId);
            if (lot == null) {
                throw new IllegalArgumentException("존재하지 않는 lotId입니다: " + lotId);
            }

            StockLot before = lot.copy();
            if (quantity != null) {
                lot.setQuantity(quantity);
            }
            if (status != null) {
                lot.setStatus(status);
            }
            stockLotDao.update(conn, lot);
            auditLogService.logUpdate(conn, before, lot, changedBy, reason);

            // 11번 자동 해결 규칙 — 관리자 직접수정으로 재고부족/재고초과가 해소됐을 수 있음
            alertResolutionService.reevaluate(conn, lot.getItemId());

            return new AdjustResult(lot.getLotId(), lot.getQuantity(), lot.getStatus());
        });
    }

    // API_명세.md 10.3 참고. 응답은 {lotId}만 돌려주면 되므로 결과 타입 없이 Long 하나로 충분.
    public Long delete(Long lotId, String reason, Long changedBy) throws SQLException {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason은 필수입니다");
        }

        return DBConnection.executeInTransactionWithResult(conn -> {
            StockLot lot = stockLotDao.findByIdForUpdate(conn, lotId);
            if (lot == null) {
                throw new IllegalArgumentException("존재하지 않는 lotId입니다: " + lotId);
            }
            if ("DELETED".equals(lot.getStatus())) {
                throw new IllegalStateException("이미 삭제된 로트입니다: lotId=" + lotId);
            }

            StockLot before = lot.copy();
            lot.setStatus("DELETED");
            stockLotDao.update(conn, lot);
            auditLogService.logDelete(conn, before, changedBy, reason);

            return lotId;
        });
    }

    public static class RestoreResult {
        public final Long lotId;
        public final Integer restoredQuantity;
        public final String restoredStatus;

        public RestoreResult(Long lotId, Integer restoredQuantity, String restoredStatus) {
            this.lotId = lotId;
            this.restoredQuantity = restoredQuantity;
            this.restoredStatus = restoredStatus;
        }
    }

    // API_명세.md 10.4 참고. 지정한 로그의 beforeValue 스냅샷으로 STOCK_LOT을 되돌리고,
    // 그 복원 자체도 새 RESTORE 로그로 남긴 뒤 원본 로그는 isReverted=true로 표시한다.
    public RestoreResult restore(Long logId, Long changedBy) throws SQLException {
        return DBConnection.executeInTransactionWithResult(conn -> {
            // 동시에 같은 로그가 두 번 복원되는 것(중복 복원)을 막기 위해 잠근다 — ApprovalService.decide()와 동일한 이유.
            StockChangeLog log = stockChangeLogDao.findByIdForUpdate(conn, logId);
            if (log == null) {
                throw new IllegalArgumentException("존재하지 않는 logId입니다: " + logId);
            }
            if (Boolean.TRUE.equals(log.getIsReverted())) {
                throw new IllegalStateException("이미 복원된 로그입니다: logId=" + logId);
            }
            if (log.getBeforeValue() == null) {
                throw new IllegalStateException("복원할 이전 상태(beforeValue)가 없는 로그입니다: logId=" + logId);
            }

            // 소프트 삭제라 10.3으로 삭제된 로트도 row 자체는 항상 존재한다 — 동시 복원/수정 방지를 위해 잠근다.
            StockLot current = stockLotDao.findByIdForUpdate(conn, log.getLotId());
            if (current == null) {
                throw new IllegalStateException("복원 대상 로트(lotId=" + log.getLotId() + ")를 찾을 수 없습니다");
            }
            StockLot beforeRestore = current.copy();

            StockLot restored = parseSnapshot(log.getBeforeValue());
            restored.setLotId(current.getLotId());
            restored.setCreatedBy(current.getCreatedBy()); // 스냅샷 JSON엔 없는 필드라 현재 값을 그대로 유지
            stockLotDao.update(conn, restored);

            auditLogService.logRestore(conn, beforeRestore, restored, changedBy,
                    "changeLog(logId=" + logId + ") 복원");

            log.setIsReverted(true);
            stockChangeLogDao.update(conn, log);

            return new RestoreResult(restored.getLotId(), restored.getQuantity(), restored.getStatus());
        });
    }

    // AuditLogService.toJson()이 만든 스냅샷을 역파싱 — 필드 구성이 그 메서드와 1:1로 대응된다.
    private StockLot parseSnapshot(String json) {
        Map<String, Object> map = JsonUtil.parseObject(json);
        StockLot lot = new StockLot();
        lot.setItemId(toLong(map.get("itemId")));
        lot.setZoneId(toLong(map.get("zoneId")));
        lot.setPartnerId(toLong(map.get("partnerId")));
        lot.setQuantity(toInteger(map.get("quantity")));
        lot.setInboundDate(LocalDate.parse((String) map.get("inboundDate")));
        Object expiryDate = map.get("expiryDate");
        lot.setExpiryDate(expiryDate != null ? LocalDate.parse((String) expiryDate) : null);
        lot.setStatus((String) map.get("status"));
        lot.setParentLotId(toLong(map.get("parentLotId")));
        return lot;
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
