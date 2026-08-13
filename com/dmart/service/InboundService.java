package com.dmart.service;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.PartnerDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Item;
import com.dmart.dto.Partner;
import com.dmart.dto.StockLot;
import com.dmart.dto.Zone;

import java.sql.SQLException;
import java.time.LocalDate;

// API_명세.md 6.1 참고. 여러 DAO를 하나의 트랜잭션으로 묶어 "입고 1건 처리"를 완성한다.
public class InboundService {

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final PartnerDao partnerDao = new PartnerDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final AlertDao alertDao = new AlertDao();
    private final AlertResolutionService alertResolutionService = new AlertResolutionService();

    public static class InboundResult {
        public final Long lotId;
        public final LocalDate expiryDate;
        public final boolean alertCreated;

        public InboundResult(Long lotId, LocalDate expiryDate, boolean alertCreated) {
            this.lotId = lotId;
            this.expiryDate = expiryDate;
            this.alertCreated = alertCreated;
        }
    }

    public InboundResult inbound(Long itemId, Long zoneId, Long partnerId, int quantity,
                                  LocalDate inboundDate, Long createdBy) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 0보다 커야 합니다");
        }

        return DBConnection.executeInTransactionWithResult(conn -> {
            Item item = itemDao.findById(conn, itemId);
            if (item == null) {
                throw new IllegalArgumentException("존재하지 않는 itemId입니다: " + itemId);
            }
            // 이후 용량 체크(현재 합계 조회 -> insert) 사이에 다른 요청이 끼어들지 못하게 잠근다.
            Zone zone = zoneDao.findByIdForUpdate(conn, zoneId);
            if (zone == null) {
                throw new IllegalArgumentException("존재하지 않는 zoneId입니다: " + zoneId);
            }
            Partner partner = partnerDao.findById(conn, partnerId);
            if (partner == null) {
                throw new IllegalArgumentException("존재하지 않는 partnerId입니다: " + partnerId);
            }
            if (!"SUPPLIER".equals(partner.getType())) {
                throw new IllegalArgumentException("partnerId=" + partnerId + "는 공급처(SUPPLIER)가 아닙니다");
            }
            if (!item.getUnit().equals(zone.getZoneName())) {
                throw new IllegalArgumentException(
                        "품목 단위(" + item.getUnit() + ")와 구역 단위(" + zone.getZoneName() + ")가 다릅니다");
            }

            boolean willExceedCapacityMax = false;
            if (item.getCapacityMax() != null) {
                int itemTotalAfter = stockLotDao.sumQuantityByItemId(conn, itemId) + quantity;
                willExceedCapacityMax = itemTotalAfter > item.getCapacityMax();
            }
            if (zone.getCapacity() != null) {
                int zoneTotalAfter = stockLotDao.sumQuantityByZoneId(conn, zoneId) + quantity;
                if (zoneTotalAfter > zone.getCapacity()) {
                    throw new IllegalStateException(
                            "구역(zoneId=" + zoneId + ") 용량(" + zone.getCapacity() + ")을 초과합니다");
                }
            }

            LocalDate expiryDate = item.getShelfLifeDays() != null
                    ? inboundDate.plusDays(item.getShelfLifeDays())
                    : null;

            StockLot lot = new StockLot();
            lot.setItemId(itemId);
            lot.setZoneId(zoneId);
            lot.setPartnerId(partnerId);
            lot.setQuantity(quantity);
            lot.setInboundDate(inboundDate);
            lot.setExpiryDate(expiryDate);
            lot.setStatus("NORMAL");
            lot.setCreatedBy(createdBy);
            Long lotId = stockLotDao.insert(conn, lot);

            boolean alertCreated = false;
            if (willExceedCapacityMax) {
                Alert alert = new Alert();
                alert.setItemId(itemId);
                alert.setAlertType("재고초과");
                alert.setMessage("품목(itemId=" + itemId + ") 재고가 capacity_max(" + item.getCapacityMax()
                        + ")를 초과했습니다");
                alert.setIsResolved(false);
                alertDao.insert(conn, alert);
                alertCreated = true;
            }

            // 11번 자동 해결 규칙 — 이번 입고로 재고부족이 해소됐을 수 있음
            alertResolutionService.reevaluate(conn, itemId);

            return new InboundResult(lotId, expiryDate, alertCreated);
        });
    }
}
