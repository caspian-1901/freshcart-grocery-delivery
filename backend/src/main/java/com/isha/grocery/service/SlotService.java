package com.isha.grocery.service;

import com.isha.grocery.domain.DeliverySlot;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.repo.DeliverySlotRepository;
import com.isha.grocery.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Delivery slot availability and reservation (Week 2).
 *
 * <p>Availability is always computed from the current {@code booked} counter, and
 * the reservation itself takes a row lock — so a slot that filled up between the
 * user loading the picker and confirming the order is rejected rather than
 * oversold.
 */
@Service
public class SlotService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE, d MMM");
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("h:mm a");

    private final DeliverySlotRepository slots;

    public SlotService(DeliverySlotRepository slots) {
        this.slots = slots;
    }

    /** Slots grouped by day, for the next {@code days} days starting today. */
    @Transactional(readOnly = true)
    public List<Responses.SlotDay> availableSlots(int days) {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(Math.max(days, 1) - 1L);
        List<DeliverySlot> found = slots.findBySlotDateBetweenOrderBySlotDateAscStartTimeAsc(today, until);

        Map<LocalDate, List<Responses.SlotView>> byDay = new LinkedHashMap<>();
        LocalTime now = LocalTime.now();
        for (DeliverySlot slot : found) {
            // A slot whose window has already started today is no longer bookable.
            if (slot.getSlotDate().equals(today) && slot.getStartTime().isBefore(now)) {
                continue;
            }
            byDay.computeIfAbsent(slot.getSlotDate(), d -> new ArrayList<>()).add(toView(slot));
        }

        List<Responses.SlotDay> result = new ArrayList<>();
        byDay.forEach((date, views) -> result.add(
                new Responses.SlotDay(date, dayLabel(date), views)));
        return result;
    }

    @Transactional(readOnly = true)
    public Responses.SlotView get(Long id) {
        return slots.findById(id)
                .map(SlotService::toView)
                .orElseThrow(() -> ApiException.notFound("That delivery slot no longer exists."));
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(Long slotId) {
        return slots.findById(slotId).map(s -> !s.isFull()).orElse(false);
    }

    /**
     * Reserves one place in the slot. Called only at order confirmation, inside
     * the confirming transaction, under a pessimistic lock.
     */
    @Transactional
    public DeliverySlot reserve(Long slotId) {
        DeliverySlot slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> ApiException.notFound("That delivery slot no longer exists."));

        if (slot.isFull()) {
            throw ApiException.conflict("SLOT_FULL",
                    "That delivery slot just filled up. Please pick another one.");
        }
        slot.setBooked(slot.getBooked() + 1);
        return slots.save(slot);
    }

    @Transactional
    public void release(Long slotId) {
        slots.findByIdForUpdate(slotId).ifPresent(slot -> {
            slot.setBooked(Math.max(slot.getBooked() - 1, 0));
            slots.save(slot);
        });
    }

    static String dayLabel(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "Today";
        }
        if (date.equals(today.plusDays(1))) {
            return "Tomorrow";
        }
        return date.format(DAY_LABEL);
    }

    static Responses.SlotView toView(DeliverySlot slot) {
        return new Responses.SlotView(
                slot.getId(),
                slot.getSlotDate(),
                slot.getStartTime().format(TIME_LABEL),
                slot.getEndTime().format(TIME_LABEL),
                slot.getCapacity(),
                slot.remaining(),
                slot.isFull());
    }
}
