package com.bottommart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PARTNER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // SUPPLIER / CUSTOMER
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "contact", length = 100)
    private String contact;
}
