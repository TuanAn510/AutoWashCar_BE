package com.shinecraft.server.loyalty;

import com.shinecraft.server.user.User;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyMonthlySnapshotRepository extends JpaRepository<LoyaltyMonthlySnapshot, Long> {
    Optional<LoyaltyMonthlySnapshot> findByCustomerAndPeriodStart(User customer, LocalDate periodStart);
}
