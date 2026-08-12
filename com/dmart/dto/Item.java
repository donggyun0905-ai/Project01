package com.dmart.dto;

public class Item {

    private Long itemId;
    private String itemName;
    private String category;
    private String unit;
    private Integer thresholdMin;
    private Integer capacityMax;
    private Integer shelfLifeDays; // 유통기한 일수, null = 유통기한 없는 품목

    public Item() {
    }

    public Item(Long itemId, String itemName, String category, String unit, Integer thresholdMin,
                Integer capacityMax, Integer shelfLifeDays) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.category = category;
        this.unit = unit;
        this.thresholdMin = thresholdMin;
        this.capacityMax = capacityMax;
        this.shelfLifeDays = shelfLifeDays;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getThresholdMin() {
        return thresholdMin;
    }

    public void setThresholdMin(Integer thresholdMin) {
        this.thresholdMin = thresholdMin;
    }

    public Integer getCapacityMax() {
        return capacityMax;
    }

    public void setCapacityMax(Integer capacityMax) {
        this.capacityMax = capacityMax;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public void setShelfLifeDays(Integer shelfLifeDays) {
        this.shelfLifeDays = shelfLifeDays;
    }

    @Override
    public String toString() {
        return "Item{itemId=" + itemId + ", itemName='" + itemName + "', category='" + category
                + "', unit='" + unit + "', thresholdMin=" + thresholdMin + ", capacityMax=" + capacityMax
                + ", shelfLifeDays=" + shelfLifeDays + "}";
    }
}
