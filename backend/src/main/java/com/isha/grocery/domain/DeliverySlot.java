package com.isha.grocery.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A bookable delivery window. {@code booked} is only ever incremented inside a
 * locked transaction so the UI can never book a slot that has just filled up
 * (Week 2 challenge: slot availability consistency).
 */
@Entity
@Table(name = "delivery_slots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"slot_date", "start_time"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliverySlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    @Builder.Default
    private int booked = 0;

    public int remaining() {
        return Math.max(capacity - booked, 0);
    }

    public boolean isFull() {
        return remaining() <= 0;
    }

    public String label() {
        return startTime.toString() + " - " + endTime.toString();
    }
}
