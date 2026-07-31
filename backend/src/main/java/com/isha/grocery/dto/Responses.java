package com.isha.grocery.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Outbound payloads. */
public final class Responses {

    private Responses() {
    }

    public record Auth(String token, Instant expiresAt, UserSummary user) {
    }

    public record UserSummary(Long id, String name, String email, String phone) {
    }

    public record Session(boolean valid, Instant expiresAt, long secondsRemaining) {
    }

    public record ItemView(Long id, String name, String category, String description,
                           String emoji, String unit, BigDecimal price,
                           int availableQuantity, boolean inStock) {
    }

    public record CartLine(Long id, Long itemId, String name, String emoji, String unit,
                           BigDecimal unitPrice, int quantity, BigDecimal lineTotal,
                           int availableQuantity, boolean stockIssue) {
    }

    public record CartView(List<CartLine> lines, BigDecimal subtotal, BigDecimal deliveryFee,
                           BigDecimal total, int itemCount, boolean hasStockIssue) {
    }

    public record SlotView(Long id, LocalDate date, String startTime, String endTime,
                           int capacity, int remaining, boolean full) {
    }

    public record SlotDay(LocalDate date, String label, List<SlotView> slots) {
    }

    public record AddressView(Long id, String label, String line1, String line2, String city,
                              String pincode, String phone, boolean defaultAddress) {
    }

    public record OrderLine(Long id, String itemName, String unit, BigDecimal unitPrice,
                            int quantity, BigDecimal lineTotal) {
    }

    public record StatusEvent(String status, String display, String note, Instant occurredAt) {
    }

    public record PaymentView(String gatewayRef, String status, BigDecimal amount,
                              String method, String resolvedVia, Instant initiatedAt,
                              Instant completedAt) {
    }

    public record OrderView(Long id, String reference, String status, String statusDisplay,
                            BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total,
                            String deliveryAddress, SlotView slot, List<OrderLine> items,
                            List<StatusEvent> timeline, PaymentView payment,
                            Instant createdAt, Instant placedAt) {
    }

    /**
     * Returned by the draft review step. Week 2: slot availability and stock are
     * re-checked here rather than trusted from the earlier page load.
     */
    public record DraftReview(OrderView order, boolean slotStillAvailable,
                              boolean stockStillAvailable, List<String> warnings) {
    }

    public record PaymentInitiation(String gatewayRef, String checkoutUrl, BigDecimal amount,
                                    int callbackTimeoutSeconds) {
    }
}
