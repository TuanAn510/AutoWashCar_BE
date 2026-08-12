package com.shinecraft.server.loyalty;

import com.shinecraft.server.user.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {
    Optional<LoyaltyAccount> findByCustomer(User customer);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from LoyaltyAccount account where account.customer = :customer")
    Optional<LoyaltyAccount> findByCustomerForUpdate(@Param("customer") User customer);
}
