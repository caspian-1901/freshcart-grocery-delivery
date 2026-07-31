package com.isha.grocery.domain;

public enum PaymentStatus {
    /** Gateway order created, waiting for the user to pay. */
    PENDING,
    /** Gateway confirmed the payment (via callback or fallback status check). */
    SUCCESS,
    FAILED
}
