package com.shinecraft.server.loyalty;

import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.user.User;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<LoyaltyTransaction> findByBookingAndType(Booking booking, LoyaltyTransactionType type);

    List<LoyaltyTransaction> findByCustomerOrderByCreatedAtDesc(User customer);

    List<LoyaltyTransaction> findByCustomerOrderByCreatedAtAsc(User customer);

    List<LoyaltyTransaction> findByTypeAndExpiresAtBeforeAndPointsGreaterThan(
            LoyaltyTransactionType type, LocalDateTime expiresAt, Integer points);

    @Query("""
            select coalesce(sum(transaction.points), 0)
            from LoyaltyTransaction transaction
            where transaction.customer = :customer
              and transaction.type = :type
            """)
    Long sumPointsByType(@Param("customer") User customer, @Param("type") LoyaltyTransactionType type);

    @Query("""
            select coalesce(sum(transaction.points), 0)
            from LoyaltyTransaction transaction
            where transaction.customer = :customer
              and transaction.type = com.shinecraft.server.loyalty.LoyaltyTransactionType.EARN
              and transaction.status = com.shinecraft.server.loyalty.LoyaltyTransactionStatus.POSTED
              and transaction.postedAt >= :since
            """)
    Long sumEarnedPointsSince(@Param("customer") User customer, @Param("since") LocalDateTime since);

    @Query("""
            select coalesce(sum(transaction.points), 0)
            from LoyaltyTransaction transaction
            where transaction.customer = :customer
              and transaction.type = com.shinecraft.server.loyalty.LoyaltyTransactionType.EARN
              and transaction.status = com.shinecraft.server.loyalty.LoyaltyTransactionStatus.POSTED
              and transaction.postedAt >= :from
              and transaction.postedAt < :to
            """)
    Long sumEarnedPointsBetween(
            @Param("customer") User customer, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select max(transaction.postedAt)
            from LoyaltyTransaction transaction
            where transaction.customer = :customer
              and transaction.type = com.shinecraft.server.loyalty.LoyaltyTransactionType.EARN
              and transaction.status = com.shinecraft.server.loyalty.LoyaltyTransactionStatus.POSTED
            """)
    LocalDateTime lastEarnedAt(@Param("customer") User customer);

    @Query("""
            select coalesce(sum(transaction.booking.finalAmount), 0)
            from LoyaltyTransaction transaction
            where transaction.customer = :customer
              and transaction.type = com.shinecraft.server.loyalty.LoyaltyTransactionType.EARN
              and transaction.status = com.shinecraft.server.loyalty.LoyaltyTransactionStatus.POSTED
              and transaction.booking is not null
              and transaction.postedAt >= :since
            """)
    BigDecimal sumEarnedSpendingSince(@Param("customer") User customer, @Param("since") LocalDateTime since);

    @Query("""
            select count(transaction)
            from LoyaltyTransaction transaction
            where transaction.customer = :customer
              and transaction.type = com.shinecraft.server.loyalty.LoyaltyTransactionType.EARN
              and transaction.status = com.shinecraft.server.loyalty.LoyaltyTransactionStatus.POSTED
              and transaction.postedAt >= :since
            """)
    Long countEarnVisitsSince(@Param("customer") User customer, @Param("since") LocalDateTime since);
}
