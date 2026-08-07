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
@Table(name = "APPROVAL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long approvalId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    // Alert that triggered this request; null when manually requested.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    // 발주 / 출고
    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    // 대기 / 승인 / 반려
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "대기";

    // Null when the system auto-suggested the request.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requested_by")
    private AppUser requestedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
