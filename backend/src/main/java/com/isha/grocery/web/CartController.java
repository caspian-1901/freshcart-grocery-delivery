package com.isha.grocery.web;

import com.isha.grocery.dto.Requests;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public Responses.CartView view() {
        return cart.view();
    }

    @PostMapping("/items")
    public Responses.CartView add(@Valid @RequestBody Requests.AddToCart request) {
        return cart.add(request.itemId(), request.quantity());
    }

    /** Absolute quantity — the server re-validates it against live stock. */
    @PutMapping("/items/{itemId}")
    public Responses.CartView update(@PathVariable Long itemId,
                                     @Valid @RequestBody Requests.UpdateQuantity request) {
        return cart.updateQuantity(itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public Responses.CartView remove(@PathVariable Long itemId) {
        return cart.remove(itemId);
    }
}
