package com.isha.grocery.service;

import com.isha.grocery.domain.Order;
import com.isha.grocery.domain.OrderStatus;
import com.isha.grocery.domain.Payment;
import com.isha.grocery.domain.PaymentStatus;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.repo.OrderRepository;
import com.isha.grocery.repo.PaymentRepository;
import com.isha.grocery.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Payment initiation, callback handling and the fallback status check.
 *
 * <p>Week 3 challenge — callback reliability: an order must never be stranded in
 * "pending" just because the gateway's callback was slow or lost. Two paths can
 * therefore resolve a payment, and both funnel into {@link #resolve}:
 * <ol>
 *   <li>the gateway callback ({@code resolvedVia = CALLBACK}), and</li>
 *   <li>a direct status query, run on demand from the payment screen and by a
 *       scheduled sweep once the callback window has passed
 *       ({@code resolvedVia = FALLBACK_STATUS_CHECK}).</li>
 * </ol>
 * Whichever arrives first wins; the other becomes a no-op.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final MockPaymentGateway gateway;
    private final OrderService orderService;
    private final CurrentUser currentUser;
    private final int callbackTimeoutSeconds;

    public PaymentService(PaymentRepository payments, OrderRepository orders,
                          MockPaymentGateway gateway, OrderService orderService,
                          CurrentUser currentUser,
                          @Value("${app.payment.callback-timeout-seconds}") int callbackTimeoutSeconds) {
        this.payments = payments;
        this.orders = orders;
        this.gateway = gateway;
        this.orderService = orderService;
        this.currentUser = currentUser;
        this.callbackTimeoutSeconds = callbackTimeoutSeconds;
    }

    // ------------------------------------------------------------------
    // Initiation
    // ------------------------------------------------------------------

    @Transactional
    public Responses.PaymentInitiation initiate(Long orderId, String method) {
        Order order = orderService.requireOwnOrder(orderId);

        if (order.getStatus() != OrderStatus.DRAFT) {
            throw ApiException.badRequest("ALREADY_PLACED", "This order has already been placed.");
        }
        if (order.getItems().isEmpty()) {
            throw ApiException.badRequest("EMPTY_CART", "This order has no items.");
        }
        if (order.getSlot() == null || order.getAddress() == null) {
            throw ApiException.badRequest("INCOMPLETE_ORDER",
                    "Please choose a delivery address and slot before paying.");
        }
        // Re-check the slot one last time before taking money (Week 2 lesson).
        if (order.getSlot().isFull()) {
            throw ApiException.conflict("SLOT_FULL",
                    "That delivery slot just filled up. Please pick another one.");
        }

        Payment payment = payments.findByOrderId(order.getId()).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
            throw ApiException.badRequest("ALREADY_PAID", "This order has already been paid for.");
        }

        String ref = gateway.createOrder(order.getTotal());
        if (payment == null) {
            payment = Payment.builder()
                    .order(order)
                    .gatewayRef(ref)
                    .amount(order.getTotal())
                    .method(method)
                    .status(PaymentStatus.PENDING)
                    .initiatedAt(Instant.now())
                    .build();
        } else {
            // Retry after a failure: new gateway attempt against the same order.
            payment.setGatewayRef(ref);
            payment.setAmount(order.getTotal());
            payment.setMethod(method);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setInitiatedAt(Instant.now());
            payment.setResolvedVia(null);
            payment.setCompletedAt(null);
        }
        payments.save(payment);
        order.setPayment(payment);

        return new Responses.PaymentInitiation(ref, "/pay/" + ref, order.getTotal(), callbackTimeoutSeconds);
    }

    // ------------------------------------------------------------------
    // Path 1: gateway callback
    // ------------------------------------------------------------------

    @Transactional
    public void handleCallback(String gatewayRef, String status, String signature) {
        if (!gateway.verifySignature(gatewayRef, signature)) {
            throw ApiException.badRequest("BAD_SIGNATURE", "Payment callback signature did not match.");
        }
        Payment payment = payments.findByGatewayRef(gatewayRef)
                .orElseThrow(() -> ApiException.notFound("Unknown payment reference."));

        PaymentStatus resolved = "SUCCESS".equalsIgnoreCase(status)
                ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        resolve(payment, resolved, "CALLBACK");
    }

    // ------------------------------------------------------------------
    // Path 2: fallback status check
    // ------------------------------------------------------------------

    /** On-demand fallback, called by the payment screen while it waits. */
    @Transactional
    public Responses.OrderView checkStatus(Long orderId) {
        Order order = orderService.requireOwnOrder(orderId);
        Payment payment = payments.findByOrderId(order.getId())
                .orElseThrow(() -> ApiException.notFound("No payment has been started for this order."));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            reconcile(payment);
        }
        return orderService.toView(orders.findById(order.getId()).orElseThrow());
    }

    /**
     * Scheduled sweep: any payment still pending past the callback window is
     * resolved by querying the gateway, so orders never sit stuck.
     */
    @Transactional
    public int reconcilePendingPayments() {
        Instant cutoff = Instant.now().minus(callbackTimeoutSeconds, ChronoUnit.SECONDS);
        List<Payment> stale = payments.findByStatusAndInitiatedAtBefore(PaymentStatus.PENDING, cutoff);

        int resolved = 0;
        for (Payment payment : stale) {
            if (reconcile(payment)) {
                resolved++;
            }
        }
        if (resolved > 0) {
            log.info("Fallback status check resolved {} payment(s) with no callback", resolved);
        }
        return resolved;
    }

    /** Queries the gateway directly and applies whatever it reports. */
    private boolean reconcile(Payment payment) {
        payment.setLastPolledAt(Instant.now());
        var record = gateway.fetchStatus(payment.getGatewayRef()).orElse(null);
        if (record == null || record.status() == PaymentStatus.PENDING) {
            payments.save(payment);
            return false;
        }
        resolve(payment, record.status(), "FALLBACK_STATUS_CHECK");
        return true;
    }

    // ------------------------------------------------------------------
    // Shared resolution
    // ------------------------------------------------------------------

    /** Idempotent: the first path to arrive resolves the payment, the rest no-op. */
    private void resolve(Payment payment, PaymentStatus status, String via) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.debug("Payment {} already {} — ignoring duplicate {}",
                    payment.getGatewayRef(), payment.getStatus(), via);
            return;
        }

        payment.setStatus(status);
        payment.setResolvedVia(via);
        payment.setCompletedAt(Instant.now());
        payments.save(payment);

        if (status == PaymentStatus.SUCCESS) {
            Order order = orders.findById(payment.getOrder().getId()).orElseThrow();
            orderService.confirmPaidOrder(order);
            log.info("Order {} confirmed via {}", order.getReference(), via);
        } else {
            log.info("Payment {} failed (via {})", payment.getGatewayRef(), via);
        }
    }

    public int callbackTimeoutSeconds() {
        return callbackTimeoutSeconds;
    }
}
