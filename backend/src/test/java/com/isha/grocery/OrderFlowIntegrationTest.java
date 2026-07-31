package com.isha.grocery;

import com.isha.grocery.domain.*;
import com.isha.grocery.dto.Requests;
import com.isha.grocery.repo.*;
import com.isha.grocery.service.*;
import com.isha.grocery.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.isha.grocery.config.AuthUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration coverage for the flow described in the weekly reports:
 * catalog -> cart -> slot -> draft -> payment -> confirmed order -> tracking.
 */
@SpringBootTest
@Transactional
class OrderFlowIntegrationTest {

    @Autowired AuthService authService;
    @Autowired CartService cartService;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired MockPaymentGateway gateway;
    @Autowired AddressService addressService;

    @Autowired UserRepository users;
    @Autowired ItemRepository items;
    @Autowired DeliverySlotRepository slots;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;

    private Item item;
    private DeliverySlot slot;
    private Long addressId;

    @BeforeEach
    void setUp() {
        String email = "test" + System.nanoTime() + "@grocery.test";
        authService.signup(new Requests.Signup("Test User", email, "password123", "9999999999"));
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        authenticateAs(user);

        item = items.save(Item.builder()
                .name("Test Rice").category("Staples").unit("1 kg")
                .price(new BigDecimal("100.00")).availableQuantity(10).active(true)
                .build());

        slot = slots.save(DeliverySlot.builder()
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(11, 0))
                .capacity(1).booked(0)
                .build());

        addressId = addressService.create(new Requests.AddressPayload(
                "Home", "1 Test Street", null, "Pune", "411001", "9999999999", true)).id();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthUser(user.getId(), user.getEmail()), null, List.of()));
    }

    // ---------------------------------------------------------------- cart

    @Test
    void cartRejectsQuantityBeyondLiveStock() {
        assertThatThrownBy(() -> cartService.add(item.getId(), 11))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("left in stock");
    }

    @Test
    void cartRevalidatesStockOnUpdateRatherThanTrustingTheClient() {
        cartService.add(item.getId(), 2);

        // Inventory drops after the item was already in the cart.
        item.setAvailableQuantity(1);
        items.save(item);

        assertThatThrownBy(() -> cartService.updateQuantity(item.getId(), 2))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("left in stock");
    }

    // --------------------------------------------------------------- draft

    @Test
    void draftCannotBeCreatedFromAnEmptyCart() {
        assertThatThrownBy(() -> orderService.createDraft(addressId, slot.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cart is empty");
    }

    @Test
    void reviewReportsASlotThatFilledUpAfterTheDraftWasCreated() {
        cartService.add(item.getId(), 1);
        var draft = orderService.createDraft(addressId, slot.getId());

        // Someone else takes the last place in the slot.
        slot.setBooked(slot.getCapacity());
        slots.save(slot);

        var review = orderService.review(draft.id());
        assertThat(review.slotStillAvailable()).isFalse();
        assertThat(review.warnings()).isNotEmpty();
    }

    @Test
    void draftSnapshotsThePriceAtTimeOfPurchase() {
        cartService.add(item.getId(), 2);
        var draft = orderService.createDraft(addressId, slot.getId());

        item.setPrice(new BigDecimal("250.00"));
        items.save(item);

        Order stored = orders.findById(draft.id()).orElseThrow();
        assertThat(stored.getItems().get(0).getUnitPrice()).isEqualByComparingTo("100.00");
    }

    // ------------------------------------------------------------- payment

    @Test
    void successfulCallbackConfirmsTheOrderAndDecrementsStock() {
        cartService.add(item.getId(), 3);
        var draft = orderService.createDraft(addressId, slot.getId());
        var initiation = paymentService.initiate(draft.id(), "UPI");

        gateway.pay(initiation.gatewayRef(), MockPaymentGateway.Outcome.SUCCESS);
        paymentService.handleCallback(initiation.gatewayRef(), "SUCCESS",
                gateway.expectedSignature(initiation.gatewayRef()));

        Order confirmed = orders.findById(draft.id()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(confirmed.getPlacedAt()).isNotNull();
        assertThat(items.findById(item.getId()).orElseThrow().getAvailableQuantity()).isEqualTo(7);
        assertThat(payments.findByOrderId(draft.id()).orElseThrow().getResolvedVia()).isEqualTo("CALLBACK");
    }

    /** The Week 3 challenge: a payment that succeeds but whose callback is lost. */
    @Test
    void lostCallbackIsResolvedByTheFallbackStatusCheck() {
        cartService.add(item.getId(), 1);
        var draft = orderService.createDraft(addressId, slot.getId());
        var initiation = paymentService.initiate(draft.id(), "CARD");

        gateway.pay(initiation.gatewayRef(), MockPaymentGateway.Outcome.SUCCESS_NO_CALLBACK);

        // No callback arrives, so the order is still a draft.
        assertThat(orders.findById(draft.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DRAFT);

        var afterCheck = paymentService.checkStatus(draft.id());

        assertThat(afterCheck.status()).isEqualTo("PLACED");
        assertThat(afterCheck.payment().resolvedVia()).isEqualTo("FALLBACK_STATUS_CHECK");
    }

    @Test
    void duplicateResolutionIsIgnored() {
        cartService.add(item.getId(), 1);
        var draft = orderService.createDraft(addressId, slot.getId());
        var initiation = paymentService.initiate(draft.id(), "UPI");

        gateway.pay(initiation.gatewayRef(), MockPaymentGateway.Outcome.SUCCESS);
        paymentService.handleCallback(initiation.gatewayRef(), "SUCCESS", null);
        // A late duplicate callback must not double-decrement stock.
        paymentService.handleCallback(initiation.gatewayRef(), "SUCCESS", null);

        assertThat(items.findById(item.getId()).orElseThrow().getAvailableQuantity()).isEqualTo(9);
    }

    @Test
    void failedPaymentLeavesTheOrderAsADraft() {
        cartService.add(item.getId(), 1);
        var draft = orderService.createDraft(addressId, slot.getId());
        var initiation = paymentService.initiate(draft.id(), "UPI");

        gateway.pay(initiation.gatewayRef(), MockPaymentGateway.Outcome.FAILURE);
        paymentService.handleCallback(initiation.gatewayRef(), "FAILED", null);

        assertThat(orders.findById(draft.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(items.findById(item.getId()).orElseThrow().getAvailableQuantity()).isEqualTo(10);
    }

    // ------------------------------------------------------------ tracking

    @Test
    void trackingTimelineIsRecordedWithTimestamps() {
        cartService.add(item.getId(), 1);
        var draft = orderService.createDraft(addressId, slot.getId());
        var initiation = paymentService.initiate(draft.id(), "UPI");
        gateway.pay(initiation.gatewayRef(), MockPaymentGateway.Outcome.SUCCESS);
        paymentService.handleCallback(initiation.gatewayRef(), "SUCCESS", null);

        orderService.advanceStatus(draft.id());
        orderService.advanceStatus(draft.id());
        var delivered = orderService.advanceStatus(draft.id());

        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.timeline()).hasSize(4);
        assertThat(delivered.timeline()).allSatisfy(e -> assertThat(e.occurredAt()).isNotNull());

        assertThatThrownBy(() -> orderService.advanceStatus(draft.id()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void slotIsReservedOnceTheOrderIsConfirmed() {
        cartService.add(item.getId(), 1);
        var draft = orderService.createDraft(addressId, slot.getId());
        var initiation = paymentService.initiate(draft.id(), "UPI");
        gateway.pay(initiation.gatewayRef(), MockPaymentGateway.Outcome.SUCCESS);
        paymentService.handleCallback(initiation.gatewayRef(), "SUCCESS", null);

        assertThat(slots.findById(slot.getId()).orElseThrow().isFull()).isTrue();
    }
}
