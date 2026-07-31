package com.isha.grocery.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Single place for the money rules so cart, draft and order all agree. */
public final class Pricing {

    public static final BigDecimal DELIVERY_FEE = new BigDecimal("25.00");
    public static final BigDecimal FREE_DELIVERY_ABOVE = new BigDecimal("500.00");

    private Pricing() {
    }

    public static BigDecimal deliveryFee(BigDecimal subtotal) {
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return subtotal.compareTo(FREE_DELIVERY_ABOVE) >= 0 ? BigDecimal.ZERO : DELIVERY_FEE;
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
