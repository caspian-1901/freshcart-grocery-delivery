package com.isha.grocery.repo;

import com.isha.grocery.domain.DeliverySlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface DeliverySlotRepository extends JpaRepository<DeliverySlot, Long> {

    List<DeliverySlot> findBySlotDateBetweenOrderBySlotDateAscStartTimeAsc(LocalDate from, LocalDate to);

    Optional<DeliverySlot> findBySlotDateAndStartTime(LocalDate slotDate, LocalTime startTime);

    /** Locked read so slot capacity can never be oversold (Week 2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DeliverySlot s where s.id = :id")
    Optional<DeliverySlot> findByIdForUpdate(@Param("id") Long id);
}
