package com.dmart.service;

import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.StockTransferDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.StockLot;
import com.dmart.dto.StockTransfer;
import com.dmart.dto.Zone;

// API_명세.md 8번 참고.
public class TransferService {

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final StockTransferDao stockTransferDao = new StockTransferDao();
    private final AuditLogService auditLogService = new AuditLogService();

    public static class TransferResult {
        public final Long transferId;
        public final boolean splitOccurred;
        public final Long newLotId;

        public TransferResult(Long transferId, boolean splitOccurred, Long newLotId) {
            this.transferId = transferId;
            this.splitOccurred = splitOccurred;
            this.newLotId = newLotId;
        }
    }

    public TransferResult transfer(Long lotId, Long fromZoneId, Long toZoneId, int quantity,
                                    Long handlerId) throws java.sql.SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 0보다 커야 합니다");
        }

        return DBConnection.executeInTransactionWithResult(conn -> {
            // 동시 이동 레이스 컨디션 방지 — 8번 구현 노트
            StockLot lot = stockLotDao.findByIdForUpdate(conn, lotId);
            if (lot == null) {
                throw new IllegalArgumentException("존재하지 않는 lotId입니다: " + lotId);
            }
            if (!"NORMAL".equals(lot.getStatus())) {
                throw new IllegalStateException("로트(lotId=" + lotId + ")는 NORMAL 상태가 아니라 이동할 수 없습니다 (status=" + lot.getStatus() + ")");
            }
            if (!lot.getZoneId().equals(fromZoneId)) {
                throw new IllegalArgumentException("로트(lotId=" + lotId + ")는 현재 zoneId=" + lot.getZoneId()
                        + "에 있는데 fromZoneId=" + fromZoneId + "로 요청됨");
            }
            Zone toZone = zoneDao.findByIdForUpdate(conn, toZoneId);
            if (toZone == null) {
                throw new IllegalArgumentException("존재하지 않는 toZoneId입니다: " + toZoneId);
            }
            Item item = itemDao.findById(conn, lot.getItemId());
            if (item == null) {
                throw new IllegalArgumentException("로트(lotId=" + lotId + ")가 참조하는 itemId=" + lot.getItemId() + "가 존재하지 않습니다");
            }
            if (!item.getUnit().equals(toZone.getZoneName())) {
                throw new IllegalArgumentException(
                        "품목 단위(" + item.getUnit() + ")와 목적지 구역 단위(" + toZone.getZoneName() + ")가 다릅니다");
            }
            if (quantity > lot.getQuantity()) {
                throw new IllegalStateException(
                        "로트 수량(" + lot.getQuantity() + ")보다 많은 수량(" + quantity + ")을 이동할 수 없습니다");
            }
            if (toZone.getCapacity() != null) {
                int zoneTotalAfter = stockLotDao.sumQuantityByZoneId(conn, toZoneId) + quantity;
                if (zoneTotalAfter > toZone.getCapacity()) {
                    throw new IllegalStateException(
                            "구역(zoneId=" + toZoneId + ") 용량(" + toZone.getCapacity() + ")을 초과합니다");
                }
            }

            boolean splitOccurred;
            Long newLotId = null;
            StockLot before = lot.copy();

            if (quantity == lot.getQuantity()) {
                // 전체 이동: zone_id만 변경
                lot.setZoneId(toZoneId);
                stockLotDao.update(conn, lot);
                auditLogService.logUpdate(conn, before, lot, handlerId, "재고이동(전체)");
                splitOccurred = false;
            } else {
                // 부분 이동: 원본은 수량만 차감(존 그대로), 이동분만큼 새 로트를 목적지 존에 생성
                lot.setQuantity(lot.getQuantity() - quantity);
                stockLotDao.update(conn, lot);
                auditLogService.logUpdate(conn, before, lot, handlerId, "재고이동(분할)");

                StockLot newLot = new StockLot();
                newLot.setItemId(lot.getItemId());
                newLot.setZoneId(toZoneId);
                newLot.setPartnerId(lot.getPartnerId());
                newLot.setQuantity(quantity);
                newLot.setInitialQuantity(quantity);
                newLot.setInboundDate(lot.getInboundDate());
                newLot.setExpiryDate(lot.getExpiryDate());
                newLot.setStatus("NORMAL");
                newLot.setCreatedBy(handlerId);
                newLot.setParentLotId(lot.getLotId());
                newLotId = stockLotDao.insert(conn, newLot);
                splitOccurred = true;
            }

            StockTransfer transfer = new StockTransfer();
            transfer.setLotId(lotId);
            transfer.setFromZoneId(fromZoneId);
            transfer.setToZoneId(toZoneId);
            transfer.setQuantity(quantity);
            transfer.setHandlerId(handlerId);
            Long transferId = stockTransferDao.insert(conn, transfer);

            return new TransferResult(transferId, splitOccurred, newLotId);
        });
    }
}
