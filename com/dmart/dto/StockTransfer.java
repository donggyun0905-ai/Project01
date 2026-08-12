package com.dmart.dto;

import java.time.LocalDateTime;

public class StockTransfer {

    private Long transferId;
    private Long lotId;
    private Long fromZoneId;
    private Long toZoneId;
    private Integer quantity;
    private Long handlerId;
    private LocalDateTime movedAt;

    public StockTransfer() {
    }

    public StockTransfer(Long transferId, Long lotId, Long fromZoneId, Long toZoneId, Integer quantity,
                          Long handlerId, LocalDateTime movedAt) {
        this.transferId = transferId;
        this.lotId = lotId;
        this.fromZoneId = fromZoneId;
        this.toZoneId = toZoneId;
        this.quantity = quantity;
        this.handlerId = handlerId;
        this.movedAt = movedAt;
    }

    public Long getTransferId() {
        return transferId;
    }

    public void setTransferId(Long transferId) {
        this.transferId = transferId;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
    }

    public Long getFromZoneId() {
        return fromZoneId;
    }

    public void setFromZoneId(Long fromZoneId) {
        this.fromZoneId = fromZoneId;
    }

    public Long getToZoneId() {
        return toZoneId;
    }

    public void setToZoneId(Long toZoneId) {
        this.toZoneId = toZoneId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public LocalDateTime getMovedAt() {
        return movedAt;
    }

    public void setMovedAt(LocalDateTime movedAt) {
        this.movedAt = movedAt;
    }

    @Override
    public String toString() {
        return "StockTransfer{transferId=" + transferId + ", lotId=" + lotId + ", fromZoneId=" + fromZoneId
                + ", toZoneId=" + toZoneId + ", quantity=" + quantity + ", handlerId=" + handlerId
                + ", movedAt=" + movedAt + "}";
    }
}
