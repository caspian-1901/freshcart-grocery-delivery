package com.isha.grocery.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A grocery item held in the godown inventory (Week 1: catalog listing must
 * expose available quantity and price).
 */
@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    private String description;

    /** Small visual stand-in for a product photo. */
    private String emoji;

    /** "1 kg", "500 g", "6 pcs" ... */
    @Column(nullable = false)
    private String unit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Godown stock. Decremented only when an order is confirmed. */
    @Column(nullable = false)
    private int availableQuantity;

    @Builder.Default
    private boolean active = true;
}
