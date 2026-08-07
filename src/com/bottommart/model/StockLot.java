package com.bottommart.model;

import java.time.LocalDate;

// 재고의 유일한 출처. 로트 생성 = 입고 처리.
public class StockLot {

    private Long lotId;
    private Long itemId;
    private Long zoneId;
    private Long partnerId;      // 입고 공급처
    private Integer quantity;
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

    @Override
    public String toString() {
        return "StockLot{lotId=" + lotId + ", itemId=" + itemId + ", zoneId=" + zoneId
                + ", partnerId=" + partnerId + ", quantity=" + quantity + ", inboundDate=" + inboundDate
                + ", expiryDate=" + expiryDate + ", status='" + status + "', createdBy=" + createdBy
                + ", parentLotId=" + parentLotId + "}";
    }
}
