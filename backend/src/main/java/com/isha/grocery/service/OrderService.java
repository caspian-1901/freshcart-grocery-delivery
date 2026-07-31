package com.isha.grocery.service;

import com.isha.grocery.domain.*;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.repo.*;
import com.isha.grocery.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order draft creation, review, confirmation and status tracking.
 *
 * <p>Week 2 brought cart + address + slot together into a single draft; Week 3
 * made a draft become a real order only once payment is verified, and added the
 * timestamped tracking timeline.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final CartRepository carts;
    private final ItemRepository items;
    private final DeliverySlotRepository slots;
    private final CartService cartService;
    private final SlotService slotService;
    private final AddressService addressService;
    private final CurrentUser currentUser;

    public OrderService(OrderRepository orders, CartRepository carts, ItemRepository items,
                        DeliverySlotRepository slots, CartService cartService,
                        SlotService slotService, AddressService addressService,
                        CurrentUser currentUser) {
        this.orders = orders;
        this.carts = carts;
        this.items = items;
        this.slots = slots;
        this.cartService = cartService;
        this.slotService = slotService;
        this.addressService = addressService;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------
    // Draft
    // ------------------------------------------------------------------

    /**
     * Builds the order draft from cart + address + slot. Week 2 validation:
     * an empty cart, or a missing address/slot, can never produce a draft.
     */
    @Transactional
    public Responses.OrderView createDraft(Long addressId, Long slotId) {
        User user = currentUser.require();
        Cart cart = cartService.cartFor(user);

        if (cart.getItems().isEmpty()) {
            throw ApiException.badRequest("EMPTY_CART",
                    "Your cart is empty. Add some items before checking out.");
        }

        Address address = addressService.requireOwned(addressId);
        DeliverySlot slot = slots.findById(slotId)
                .orElseThrow(() -> ApiException.notFound("Please choose a valid delivery slot."));

        if (slot.isFull()) {
            throw ApiException.conflict("SLOT_FULL",
                    "That delivery slot just filled up. Please pick another one.");
        }

        // One live draft per user: reuse it so repeated checkout visits don't pile up.
        Order order = orders.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), OrderStatus.DRAFT)
                .orElseGet(() -> Order.builder()
                        .user(user)
                        .reference(newReference())
                        .status(OrderStatus.DRAFT)
                        .createdAt(Instant.now())
                        .build());

        order.getItems().clear();
        order.setAddress(address);
        order.setDeliveryAddressSnapshot(address.getLabel() + " — " + address.asSingleLine());
        order.setSlot(slot);
        order.setUpdatedAt(Instant.now());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem line : cart.getItems()) {
            Item item = line.getItem();
            cartService.requireStock(item, line.getQuantity());

            BigDecimal lineTotal = Pricing.money(
                    item.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            subtotal = subtotal.add(lineTotal);

            // Price and name are snapshotted here — the Week 1 schema decision.
            order.addItem(OrderItem.builder()
                    .item(item)
                    .itemName(item.getName())
                    .unit(item.getUnit())
                    .unitPrice(item.getPrice())
                    .quantity(line.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        BigDecimal fee = Pricing.deliveryFee(subtotal);
        order.setSubtotal(Pricing.money(subtotal));
        order.setDeliveryFee(Pricing.money(fee));
        order.setTotal(Pricing.money(subtotal.add(fee)));

        return toView(orders.save(order));
    }

    /**
     * Week 2 fix: slot availability and stock are re-fetched at the review step
     * rather than trusted from the earlier page load, so the user never submits
     * against state that has since gone stale.
     */
    @Transactional(readOnly = true)
    public Responses.DraftReview review(Long orderId) {
        Order order = requireOwnOrder(orderId);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw ApiException.badRequest("NOT_A_DRAFT", "This order has already been placed.");
        }

        List<String> warnings = new ArrayList<>();

        boolean slotAvailable = true;
        if (order.getSlot() != null) {
            DeliverySlot fresh = slots.findById(order.getSlot().getId()).orElse(null);
            slotAvailable = fresh != null && !fresh.isFull();
            if (!slotAvailable) {
                warnings.add("Your delivery slot filled up while you were checking out. Please pick another one.");
            }
        } else {
            slotAvailable = false;
            warnings.add("Please choose a delivery slot.");
        }

        boolean stockAvailable = true;
        for (OrderItem line : order.getItems()) {
            Item fresh = line.getItem() == null ? null : items.findById(line.getItem().getId()).orElse(null);
            if (fresh == null || !fresh.isActive()) {
                stockAvailable = false;
                warnings.add(line.getItemName() + " is no longer available.");
            } else if (fresh.getAvailableQuantity() < line.getQuantity()) {
                stockAvailable = false;
                warnings.add("Only " + fresh.getAvailableQuantity() + " " + fresh.getUnit()
                        + " of " + fresh.getName() + " left — please update your cart.");
            }
        }

        return new Responses.DraftReview(toView(order), slotAvailable, stockAvailable, warnings);
    }

    // ------------------------------------------------------------------
    // Confirmation (called by PaymentService once payment is verified)
    // ------------------------------------------------------------------

    /**
     * Turns a paid draft into a placed order: stock is decremented under a lock,
     * the slot is reserved, and the cart is emptied — all in one transaction.
     */
    @Transactional
    public Order confirmPaidOrder(Order order) {
        if (order.getStatus() != OrderStatus.DRAFT) {
            log.debug("Order {} already confirmed, skipping", order.getReference());
            return order;
        }

        // Final authoritative stock check at the point of write (Week 2 lesson).
        for (OrderItem line : order.getItems()) {
            if (line.getItem() == null) {
                continue;
            }
            Item item = items.findByIdForUpdate(line.getItem().getId())
                    .orElseThrow(() -> ApiException.conflict("ITEM_UNAVAILABLE",
                            line.getItemName() + " is no longer available."));
            cartService.requireStock(item, line.getQuantity());
            item.setAvailableQuantity(item.getAvailableQuantity() - line.getQuantity());
            items.save(item);
        }

        if (order.getSlot() != null && !order.isSlotReserved()) {
            slotService.reserve(order.getSlot().getId());
            order.setSlotReserved(true);
        }

        order.setPlacedAt(Instant.now());
        order.recordStatus(OrderStatus.PLACED, "Payment confirmed. Your order has been placed.");

        carts.findByUserId(order.getUser().getId()).ifPresent(cartService::clear);

        return orders.save(order);
    }

    // ------------------------------------------------------------------
    // Tracking
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Responses.OrderView> myOrders() {
        return orders.findByUserIdAndStatusNotOrderByCreatedAtDesc(currentUser.id(), OrderStatus.DRAFT)
                .stream().map(this::toView).toList();
    }

    /** Always read fresh from the database — never from a cached client state (Week 3). */
    @Transactional(readOnly = true)
    public Responses.OrderView get(Long orderId) {
        return toView(requireOwnOrder(orderId));
    }

    /**
     * Moves an order to its next tracking state. In a production system this
     * would be driven by the warehouse/delivery apps; the capstone exposes it so
     * the tracking timeline can be demonstrated end to end.
     */
    @Transactional
    public Responses.OrderView advanceStatus(Long orderId) {
        Order order = requireOwnOrder(orderId);
        OrderStatus next = order.getStatus().next();
        if (next == null) {
            throw ApiException.badRequest("NO_NEXT_STATUS",
                    "This order is already " + order.getStatus().display().toLowerCase() + ".");
        }
        order.recordStatus(next, noteFor(next));
        return toView(orders.save(order));
    }

    @Transactional
    public Responses.OrderView cancel(Long orderId) {
        Order order = requireOwnOrder(orderId);
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY) {
            throw ApiException.badRequest("CANNOT_CANCEL",
                    "This order is already on its way and can no longer be cancelled.");
        }

        // Put the stock and the slot place back.
        for (OrderItem line : order.getItems()) {
            if (line.getItem() == null) {
                continue;
            }
            items.findByIdForUpdate(line.getItem().getId()).ifPresent(item -> {
                item.setAvailableQuantity(item.getAvailableQuantity() + line.getQuantity());
                items.save(item);
            });
        }
        if (order.isSlotReserved() && order.getSlot() != null) {
            slotService.release(order.getSlot().getId());
            order.setSlotReserved(false);
        }

        order.recordStatus(OrderStatus.CANCELLED, "Order cancelled.");
        return toView(orders.save(order));
    }

    private String noteFor(OrderStatus status) {
        return switch (status) {
            case PACKED -> "Your items have been picked and packed at the godown.";
            case OUT_FOR_DELIVERY -> "Your order is on its way.";
            case DELIVERED -> "Delivered. Enjoy your groceries!";
            default -> null;
        };
    }

    public Order requireOwnOrder(Long orderId) {
        return orders.findByIdAndUserId(orderId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    static String newReference() {
        StringBuilder sb = new StringBuilder("GRO-");
        for (int i = 0; i < 6; i++) {
            sb.append(REF_ALPHABET.charAt(RANDOM.nextInt(REF_ALPHABET.length())));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    public Responses.OrderView toView(Order order) {
        List<Responses.OrderLine> lines = order.getItems().stream()
                .map(l -> new Responses.OrderLine(l.getId(), l.getItemName(), l.getUnit(),
                        l.getUnitPrice(), l.getQuantity(), l.getLineTotal()))
                .toList();

        List<Responses.StatusEvent> timeline = order.getStatusHistory().stream()
                .sorted((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()))
                .map(e -> new Responses.StatusEvent(e.getStatus().name(), e.getStatus().display(),
                        e.getNote(), e.getOccurredAt()))
                .toList();

        Responses.SlotView slotView = order.getSlot() == null ? null : SlotService.toView(order.getSlot());

        Payment payment = order.getPayment();
        Responses.PaymentView paymentView = payment == null ? null : new Responses.PaymentView(
                payment.getGatewayRef(), payment.getStatus().name(), payment.getAmount(),
                payment.getMethod(), payment.getResolvedVia(), payment.getInitiatedAt(),
                payment.getCompletedAt());

        return new Responses.OrderView(
                order.getId(), order.getReference(), order.getStatus().name(),
                order.getStatus().display(), order.getSubtotal(), order.getDeliveryFee(),
                order.getTotal(), order.getDeliveryAddressSnapshot(), slotView, lines,
                timeline, paymentView, order.getCreatedAt(), order.getPlacedAt());
    }
}
