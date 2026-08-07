package com.bottommart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// STOCK_LOT is the single source of truth for stock quantity.
// Creating a lot IS the inbound event (no separate INBOUND table).
@Entity
@Table(name = "STOCK_LOT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_id")
    private Long lotId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    // Supplier this lot was received from (Partner.type = SUPPLIER)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // FIFO order basis
    @Column(name = "inbound_date", nullable = false)
    private LocalDate inboundDate;

    // FEFO order basis
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // NORMAL / DISPOSED / RETURNED
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "NORMAL";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    // Self-reference: set when this lot was split off another lot during a partial zone-to-zone transfer.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_lot_id")
    private StockLot parentLot;
}
