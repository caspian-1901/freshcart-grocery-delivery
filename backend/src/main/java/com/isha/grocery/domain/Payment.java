package com.isha.grocery.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    /** Gateway-side identifier returned when the payment is initiated. */
    @Column(nullable = false, unique = true)
    private String gatewayRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    private String method;

    /** How the final status was learned: CALLBACK or FALLBACK_STATUS_CHECK. */
    private String resolvedVia;

    @Column(nullable = false)
    @Builder.Default
    private Instant initiatedAt = Instant.now();

    private Instant completedAt;

    private Instant lastPolledAt;
}
