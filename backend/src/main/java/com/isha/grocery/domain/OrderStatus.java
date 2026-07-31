package com.isha.grocery.domain;

/**
 * Order lifecycle. An order stays in DRAFT until payment is verified, then
 * moves through the tracking states defined in Week 3.
 */
public enum OrderStatus {
    DRAFT,
    PLACED,
    PACKED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    /** The forward-only tracking timeline shown to the user. */
    public static final OrderStatus[] TRACKING_FLOW = {
            PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED
    };

    public OrderStatus next() {
        return switch (this) {
            case PLACED -> PACKED;
            case PACKED -> OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> DELIVERED;
            default -> null;
        };
    }

    public String display() {
        return switch (this) {
            case DRAFT -> "Draft";
            case PLACED -> "Placed";
            case PACKED -> "Packed";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
        };
    }
}
