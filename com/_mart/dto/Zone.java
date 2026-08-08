package com._mart.dto;

public class Zone {

    private Long zoneId;
    private Long warehouseId;
    private String zoneName;
    private Integer capacity;

    public Zone() {
    }

    public Zone(Long zoneId, Long warehouseId, String zoneName, Integer capacity) {
        this.zoneId = zoneId;
        this.warehouseId = warehouseId;
        this.zoneName = zoneName;
        this.capacity = capacity;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    @Override
    public String toString() {
        return "Zone{zoneId=" + zoneId + ", warehouseId=" + warehouseId + ", zoneName='" + zoneName
                + "', capacity=" + capacity + "}";
    }
}
