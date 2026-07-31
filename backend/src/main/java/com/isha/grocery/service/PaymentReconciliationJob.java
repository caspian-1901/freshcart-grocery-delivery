package com.isha.grocery.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the Week 3 fallback: sweeps payments whose callback never arrived and
 * asks the gateway directly, so no order stays stuck in "pending".
 */
@Component
public class PaymentReconciliationJob {

    private final PaymentService payments;

    public PaymentReconciliationJob(PaymentService payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${app.payment.reconcile-interval-ms}", initialDelay = 15000)
    public void run() {
        payments.reconcilePendingPayments();
    }
}
