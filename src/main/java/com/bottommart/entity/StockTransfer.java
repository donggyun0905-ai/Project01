package com.bottommart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "STOCK_TRANSFER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long transferId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lot_id", nullable = false)
    private StockLot lot;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "from_zone_id", nullable = false)
    private Zone fromZone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "to_zone_id", nullable = false)
    private Zone toZone;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "handler_id", nullable = false)
    private AppUser handler;

    @CreationTimestamp
    @Column(name = "moved_at", nullable = false, updatable = false)
    private LocalDateTime movedAt;
}
