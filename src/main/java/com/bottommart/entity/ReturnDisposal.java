package com.bottommart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "RETURN_DISPOSAL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnDisposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lot_id", nullable = false)
    private StockLot lot;

    // 반품 / 폐기
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    // 고객반품 / 공급처반품 / 파손 / 유통기한만료 등
    @Column(name = "reason", nullable = false, length = 100)
    private String reason;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "processed_by", nullable = false)
    private AppUser processedBy;

    @Column(name = "processed_date", nullable = false)
    private LocalDate processedDate;
}
