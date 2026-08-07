package com.bottommart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Item holds master data only. Actual stock quantities live in StockLot.
@Entity
@Table(name = "ITEM")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    // Low-stock alert threshold
    @Column(name = "threshold_min")
    private Integer thresholdMin;

    // Over-stock alert threshold
    @Column(name = "capacity_max")
    private Integer capacityMax;
}
