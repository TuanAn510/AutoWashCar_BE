package com.shinecraft.server.loyalty;

import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "loyalty_transactions")
public class LoyaltyTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redemption_id")
    private RewardRedemption redemption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoyaltyTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoyaltyTransactionStatus status = LoyaltyTransactionStatus.POSTED;

    @Column(nullable = false)
    private Integer points;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    private LocalDateTime expiresAt;

    private LocalDateTime postedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        if (status == LoyaltyTransactionStatus.POSTED && postedAt == null) {
            postedAt = now;
        }
    }
}
