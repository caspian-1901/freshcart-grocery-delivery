package com.isha.grocery.service;

import com.isha.grocery.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for a real payment gateway (Razorpay/Stripe-style).
 *
 * <p>It deliberately reproduces the behaviour that caused trouble in Week 3: a
 * payment can succeed at the gateway while its callback is delayed or never
 * arrives. {@link #pay} therefore takes an outcome, including
 * {@code SUCCESS_NO_CALLBACK}, which is what exercises the fallback status
 * check. Swapping this class for a real SDK client only means reimplementing
 * {@code createOrder}, {@code fetchStatus} and verifying a real signature.
 */
@Component
public class MockPaymentGateway {

    public enum Outcome {
        /** Pays and delivers the callback immediately — the happy path. */
        SUCCESS,
        /** Pays but never delivers a callback — resolved by the fallback check. */
        SUCCESS_NO_CALLBACK,
        FAILURE
    }

    /** What the gateway itself believes about a payment. */
    public record GatewayRecord(String ref, BigDecimal amount, PaymentStatus status,
                                boolean callbackDelivered) {
    }

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, GatewayRecord> ledger = new ConcurrentHashMap<>();

    public String createOrder(BigDecimal amount) {
        String ref = "pay_" + Long.toHexString(RANDOM.nextLong() & 0xFFFFFFFFFFFFL);
        ledger.put(ref, new GatewayRecord(ref, amount, PaymentStatus.PENDING, false));
        log.debug("Gateway order created {} for {}", ref, amount);
        return ref;
    }

    /**
     * Simulates the user completing (or failing) payment on the gateway's page.
     *
     * @return true when the gateway will deliver a callback for this payment
     */
    public boolean pay(String ref, Outcome outcome) {
        GatewayRecord record = ledger.get(ref);
        if (record == null) {
            return false;
        }
        PaymentStatus status = outcome == Outcome.FAILURE ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;
        boolean deliversCallback = outcome != Outcome.SUCCESS_NO_CALLBACK;
        ledger.put(ref, new GatewayRecord(ref, record.amount(), status, deliversCallback));

        if (!deliversCallback) {
            log.warn("Gateway {} paid but callback intentionally withheld — fallback check will resolve it", ref);
        }
        return deliversCallback;
    }

    /** The fallback path: ask the gateway directly what happened (Week 3). */
    public Optional<GatewayRecord> fetchStatus(String ref) {
        return Optional.ofNullable(ledger.get(ref));
    }

    /** Mock signature check — a real gateway would HMAC the payload. */
    public boolean verifySignature(String ref, String signature) {
        return signature == null || signature.isBlank() || signature.equals(expectedSignature(ref));
    }

    public String expectedSignature(String ref) {
        return Integer.toHexString(("sig:" + ref).hashCode());
    }
}
