package com.shinecraft.server.loyalty;

import com.shinecraft.server.user.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {
    @Query("select a from LoyaltyAccount a left join fetch a.membershipTier where a.customer = :customer")
    Optional<LoyaltyAccount> findByCustomer(@Param("customer") User customer);

    @Query("select a from LoyaltyAccount a left join fetch a.membershipTier where a.customer = :customer")
    Optional<LoyaltyAccount> findByCustomerReadOnly(@Param("customer") User customer);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from LoyaltyAccount account left join fetch account.membershipTier where account.customer = :customer")
    Optional<LoyaltyAccount> findByCustomerForUpdate(@Param("customer") User customer);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoyaltyAccount a set a.membershipTier = :tier where a.id = :id")
    void updateMembershipTier(@Param("id") Long id, @Param("tier") MembershipTier tier);
}
