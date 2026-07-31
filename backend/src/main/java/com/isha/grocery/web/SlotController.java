package com.isha.grocery.web;

import com.isha.grocery.dto.Responses;
import com.isha.grocery.service.SlotService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Delivery_Slots API (Week 2): available slots by date and time window. */
@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slots;

    public SlotController(SlotService slots) {
        this.slots = slots;
    }

    @GetMapping
    public List<Responses.SlotDay> slots(@RequestParam(defaultValue = "4") int days) {
        return slots.availableSlots(days);
    }

    @GetMapping("/{id}")
    public Responses.SlotView slot(@PathVariable Long id) {
        return slots.get(id);
    }
}
