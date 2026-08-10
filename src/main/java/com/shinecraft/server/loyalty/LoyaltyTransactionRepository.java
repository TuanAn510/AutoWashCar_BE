package com.shinecraft.server.loyalty;

import com.shinecraft.server.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {
    List<LoyaltyTransaction> findByCustomerOrderByCreatedAtDesc(User customer);

    List<LoyaltyTransaction> findByTypeAndExpiresAtBeforeAndPointsGreaterThan(
            LoyaltyTransactionType type, LocalDateTime expiresAt, Integer points);
}
