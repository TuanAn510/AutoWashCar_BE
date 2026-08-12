package com.shinecraft.server.loyalty;

import com.shinecraft.server.common.BaseEntity;
import com.shinecraft.server.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "loyalty_accounts")
public class LoyaltyAccount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @Column(nullable = false)
    private Integer currentPoints = 0;

    @Column(nullable = false)
    private Integer lifetimePoints = 0;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalSpending = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer visitCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_tier_id")
    private MembershipTier membershipTier;

    private LocalDateTime lastReviewedAt;

    @Version
    private Long version = 0L;
}
