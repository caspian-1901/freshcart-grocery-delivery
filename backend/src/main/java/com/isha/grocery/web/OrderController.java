package com.isha.grocery.web;

import com.isha.grocery.dto.Requests;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** Cart + address + slot -> one order draft (Week 2). */
    @PostMapping("/draft")
    public Responses.OrderView createDraft(@Valid @RequestBody Requests.CreateDraft request) {
        return orders.createDraft(request.addressId(), request.slotId());
    }

    /** Re-checks slot availability and stock before the user commits (Week 2). */
    @GetMapping("/{id}/review")
    public Responses.DraftReview review(@PathVariable Long id) {
        return orders.review(id);
    }

    @GetMapping
    public List<Responses.OrderView> myOrders() {
        return orders.myOrders();
    }

    /** Tracking screens call this on every view so status is never stale (Week 3). */
    @GetMapping("/{id}")
    public Responses.OrderView get(@PathVariable Long id) {
        return orders.get(id);
    }

    /** Demo hook: moves the order to its next tracking state. */
    @PostMapping("/{id}/advance")
    public Responses.OrderView advance(@PathVariable Long id) {
        return orders.advanceStatus(id);
    }

    @PostMapping("/{id}/cancel")
    public Responses.OrderView cancel(@PathVariable Long id) {
        return orders.cancel(id);
    }
}
