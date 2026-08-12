package com.shinecraft.server.loyalty;

import com.shinecraft.server.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, Long> {
    List<RewardRedemption> findByCustomerOrderByRedeemedAtDesc(User customer);

    Optional<RewardRedemption> findByIdAndCustomerAndStatus(
            Long id, User customer, RewardRedemptionStatus status);

    List<RewardRedemption> findByStatusAndExpiresAtBefore(RewardRedemptionStatus status, LocalDateTime expiresAt);
}
