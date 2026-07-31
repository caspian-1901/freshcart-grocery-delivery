package com.isha.grocery.service;

import com.isha.grocery.domain.Cart;
import com.isha.grocery.domain.CartItem;
import com.isha.grocery.domain.Item;
import com.isha.grocery.domain.User;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.repo.CartItemRepository;
import com.isha.grocery.repo.CartRepository;
import com.isha.grocery.repo.ItemRepository;
import com.isha.grocery.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cart add / update / remove.
 *
 * <p>Week 2 challenge — stock validation race conditions: quantity submitted by
 * the client is never trusted. Every write re-reads the item row under a
 * pessimistic lock and validates the requested quantity against live inventory
 * inside the same transaction, so two concurrent updates cannot both succeed
 * against the same remaining stock.
 */
@Service
public class CartService {

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ItemRepository items;
    private final CurrentUser currentUser;

    public CartService(CartRepository carts, CartItemRepository cartItems,
                       ItemRepository items, CurrentUser currentUser) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.items = items;
        this.currentUser = currentUser;
    }

    @Transactional
    public Cart cartFor(User user) {
        return carts.findByUserId(user.getId())
                .orElseGet(() -> carts.save(Cart.builder().user(user).build()));
    }

    @Transactional(readOnly = true)
    public Responses.CartView view() {
        Cart cart = carts.findByUserId(currentUser.id())
                .orElseGet(() -> Cart.builder().user(currentUser.require()).build());
        return toView(cart);
    }

    @Transactional
    public Responses.CartView add(Long itemId, int quantity) {
        if (quantity < 1) {
            throw ApiException.badRequest("INVALID_QUANTITY", "Quantity must be at least 1.");
        }
        Cart cart = cartFor(currentUser.require());

        // Locked read: this is the authoritative stock figure for this transaction.
        Item item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> ApiException.notFound("That item is no longer in the catalog."));

        CartItem line = cartItems.findByCartIdAndItemId(cart.getId(), itemId).orElse(null);
        int existing = line == null ? 0 : line.getQuantity();
        int requested = existing + quantity;

        requireStock(item, requested);

        if (line == null) {
            line = CartItem.builder().cart(cart).item(item).quantity(requested).build();
            cartItems.save(line);
            cart.getItems().add(line);
        } else {
            line.setQuantity(requested);
            cartItems.save(line);
        }

        return toView(cart);
    }

    /** Absolute quantity set (0 removes the line). */
    @Transactional
    public Responses.CartView updateQuantity(Long itemId, int quantity) {
        Cart cart = cartFor(currentUser.require());
        CartItem line = cartItems.findByCartIdAndItemId(cart.getId(), itemId)
                .orElseThrow(() -> ApiException.notFound("That item is not in your cart."));

        if (quantity <= 0) {
            cart.getItems().removeIf(l -> l.getId().equals(line.getId()));
            cartItems.delete(line);
            return toView(cart);
        }

        Item item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> ApiException.notFound("That item is no longer in the catalog."));
        requireStock(item, quantity);

        line.setQuantity(quantity);
        cartItems.save(line);
        return toView(cart);
    }

    @Transactional
    public Responses.CartView remove(Long itemId) {
        return updateQuantity(itemId, 0);
    }

    @Transactional
    public void clear(Cart cart) {
        List<CartItem> lines = new ArrayList<>(cart.getItems());
        cart.getItems().clear();
        cartItems.deleteAll(lines);
    }

    /** Server-side stock gate used by both cart writes and order confirmation. */
    void requireStock(Item item, int requested) {
        if (!item.isActive()) {
            throw ApiException.conflict("ITEM_UNAVAILABLE", item.getName() + " is no longer available.");
        }
        if (requested > item.getAvailableQuantity()) {
            throw ApiException.conflict("INSUFFICIENT_STOCK",
                    "Only " + item.getAvailableQuantity() + " " + item.getUnit()
                            + " of " + item.getName() + " left in stock.");
        }
    }

    Responses.CartView toView(Cart cart) {
        List<Responses.CartLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean stockIssue = false;
        int count = 0;

        for (CartItem line : cart.getItems()) {
            Item item = line.getItem();
            BigDecimal lineTotal = Pricing.money(item.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            subtotal = subtotal.add(lineTotal);
            count += line.getQuantity();

            boolean issue = line.getQuantity() > item.getAvailableQuantity() || !item.isActive();
            stockIssue = stockIssue || issue;

            lines.add(new Responses.CartLine(
                    line.getId(), item.getId(), item.getName(), item.getEmoji(), item.getUnit(),
                    item.getPrice(), line.getQuantity(), lineTotal,
                    item.getAvailableQuantity(), issue));
        }

        lines.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        BigDecimal fee = Pricing.deliveryFee(subtotal);
        return new Responses.CartView(lines, Pricing.money(subtotal), Pricing.money(fee),
                Pricing.money(subtotal.add(fee)), count, stockIssue);
    }
}
