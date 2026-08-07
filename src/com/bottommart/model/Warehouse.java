package com.bottommart.model;

public class Warehouse {

    private Long warehouseId;
    private String name;
    private String location;

    public Warehouse() {
    }

    public Warehouse(Long warehouseId, String name, String location) {
        this.warehouseId = warehouseId;
        this.name = name;
        this.location = location;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "Warehouse{warehouseId=" + warehouseId + ", name='" + name + "', location='" + location + "'}";
    }
}
