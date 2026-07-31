package com.isha.grocery.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One timestamped entry in an order's tracking timeline (Week 3). */
@Entity
@Table(name = "order_status_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String note;

    @Column(nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();
}
