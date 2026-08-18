package com.shinecraft.server.loyalty;

import com.shinecraft.server.user.User;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PointLotRepository extends JpaRepository<PointLot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PointLot> findByCustomerAndEarnTransactionStatusAndRemainingPointsGreaterThanOrderByExpiresAtAscIdAsc(
            User customer, LoyaltyTransactionStatus status, Integer points);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PointLot> findByEarnTransactionStatusAndExpiresAtBeforeAndRemainingPointsGreaterThanOrderByExpiresAtAscIdAsc(
            LoyaltyTransactionStatus status, LocalDateTime expiresAt, Integer points);
}
