package com.isha.grocery.repo;

import com.isha.grocery.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartIdAndItemId(Long cartId, Long itemId);
}
