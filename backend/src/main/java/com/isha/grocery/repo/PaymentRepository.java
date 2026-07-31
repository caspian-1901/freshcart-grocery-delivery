package com.isha.grocery.repo;

import com.isha.grocery.domain.Payment;
import com.isha.grocery.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByGatewayRef(String gatewayRef);

    /**
     * Week 3 fallback: payments still pending past the callback window get
     * reconciled by querying the gateway directly.
     */
    List<Payment> findByStatusAndInitiatedAtBefore(PaymentStatus status, Instant cutoff);
}
