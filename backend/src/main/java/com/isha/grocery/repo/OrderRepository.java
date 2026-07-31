package com.isha.grocery.repo;

import com.isha.grocery.domain.Order;
import com.isha.grocery.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, OrderStatus status);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Optional<Order> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

    Optional<Order> findByReference(String reference);
}
