package com._mart.dto;

// STAFF의 담당 창고 배정 (N:M). 복합키(user_id, warehouse_id)라 별도 auto-id가 없음.
public class UserWarehouse {

    private Long userId;
    private Long warehouseId;

    public UserWarehouse() {
    }

    public UserWarehouse(Long userId, Long warehouseId) {
        this.userId = userId;
        this.warehouseId = warehouseId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    @Override
    public String toString() {
        return "UserWarehouse{userId=" + userId + ", warehouseId=" + warehouseId + "}";
    }
}
