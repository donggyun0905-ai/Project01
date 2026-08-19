package com.dmart.dto;

import java.time.LocalDate;

// 재고의 유일한 출처. 로트 생성 = 입고 처리.
public class StockLot {

    private Long lotId;
    private Long itemId;
    private Long zoneId;
    private Long partnerId;      // 입고 공급처
    private Integer quantity;
    private Integer initialQuantity; // 로트 생성 시점의 최초 수량. quantity와 달리 이후 변하지 않음.
    private LocalDate inboundDate; // FIFO 기준
    private LocalDate expiryDate;  // FEFO 기준
    private String status;         // NORMAL / DISPOSED / RETURNED
    private Long createdBy;
    private Long parentLotId;      // 분할 이동 시 원본 로트 참조 (nullable)

    public StockLot() {
    }

    public StockLot(Long lotId, Long itemId, Long zoneId, Long partnerId, Integer quantity,
                     LocalDate inboundDate, LocalDate expiryDate, String status, Long createdBy, Long parentLotId) {
        this.lotId = lotId;
        this.itemId = itemId;
        this.zoneId = zoneId;
        this.partnerId = partnerId;
        this.quantity = quantity;
        this.inboundDate = inboundDate;
        this.expiryDate = expiryDate;
        this.status = status;
        this.createdBy = createdBy;
        this.parentLotId = parentLotId;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getInitialQuantity() {
        return initialQuantity;
    }

    public void setInitialQuantity(Integer initialQuantity) {
        this.initialQuantity = initialQuantity;
    }

    public LocalDate getInboundDate() {
        return inboundDate;
    }

    public void setInboundDate(LocalDate inboundDate) {
        this.inboundDate = inboundDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getParentLotId() {
        return parentLotId;
    }

    public void setParentLotId(Long parentLotId) {
        this.parentLotId = parentLotId;
    }

    // AuditLogService의 before/after 스냅샷용 — 원본 필드가 나중에 바뀌어도 영향받지 않는 독립된 복사본.
    public StockLot copy() {
        StockLot c = new StockLot();
        c.setLotId(lotId);
        c.setItemId(itemId);
        c.setZoneId(zoneId);
        c.setPartnerId(partnerId);
        c.setQuantity(quantity);
        c.setInitialQuantity(initialQuantity);
        c.setInboundDate(inboundDate);
        c.setExpiryDate(expiryDate);
        c.setStatus(status);
        c.setCreatedBy(createdBy);
        c.setParentLotId(parentLotId);
        return c;
    }

    @Override
    public String toString() {
        return "StockLot{lotId=" + lotId + ", itemId=" + itemId + ", zoneId=" + zoneId
                + ", partnerId=" + partnerId + ", quantity=" + quantity + ", initialQuantity=" + initialQuantity
                + ", inboundDate=" + inboundDate
                + ", expiryDate=" + expiryDate + ", status='" + status + "', createdBy=" + createdBy
                + ", parentLotId=" + parentLotId + "}";
    }
}
