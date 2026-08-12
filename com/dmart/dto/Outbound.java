package com.dmart.dto;

import java.time.LocalDate;

public class Outbound {

    private Long outboundId;
    private Long lotId;
    private Long partnerId; // 출고 목적지 (고객)
    private Integer quantity;
    private LocalDate outboundDate;
    private Long createdBy;

    public Outbound() {
    }

    public Outbound(Long outboundId, Long lotId, Long partnerId, Integer quantity, LocalDate outboundDate, Long createdBy) {
        this.outboundId = outboundId;
        this.lotId = lotId;
        this.partnerId = partnerId;
        this.quantity = quantity;
        this.outboundDate = outboundDate;
        this.createdBy = createdBy;
    }

    public Long getOutboundId() {
        return outboundId;
    }

    public void setOutboundId(Long outboundId) {
        this.outboundId = outboundId;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
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

    public LocalDate getOutboundDate() {
        return outboundDate;
    }

    public void setOutboundDate(LocalDate outboundDate) {
        this.outboundDate = outboundDate;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "Outbound{outboundId=" + outboundId + ", lotId=" + lotId + ", partnerId=" + partnerId
                + ", quantity=" + quantity + ", outboundDate=" + outboundDate + ", createdBy=" + createdBy + "}";
    }
}
