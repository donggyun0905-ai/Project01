package com._mart.dto;

import java.time.LocalDate;

public class ReturnDisposal {

    private Long recordId;
    private Long lotId;
    private String type;   // 반품 / 폐기
    private String reason; // 고객반품 / 공급처반품 / 파손 / 유통기한만료 등
    private Integer quantity;
    private Long processedBy;
    private LocalDate processedDate;

    public ReturnDisposal() {
    }

    public ReturnDisposal(Long recordId, Long lotId, String type, String reason, Integer quantity,
                           Long processedBy, LocalDate processedDate) {
        this.recordId = recordId;
        this.lotId = lotId;
        this.type = type;
        this.reason = reason;
        this.quantity = quantity;
        this.processedBy = processedBy;
        this.processedDate = processedDate;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(Long processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDate getProcessedDate() {
        return processedDate;
    }

    public void setProcessedDate(LocalDate processedDate) {
        this.processedDate = processedDate;
    }

    @Override
    public String toString() {
        return "ReturnDisposal{recordId=" + recordId + ", lotId=" + lotId + ", type='" + type
                + "', reason='" + reason + "', quantity=" + quantity + ", processedBy=" + processedBy
                + ", processedDate=" + processedDate + "}";
    }
}
