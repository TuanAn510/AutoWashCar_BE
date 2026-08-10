package com.shinecraft.server.loyalty;

import com.shinecraft.server.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {
    Optional<LoyaltyAccount> findByCustomer(User customer);
}
