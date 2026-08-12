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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "loyalty_monthly_snapshots")
public class LoyaltyMonthlySnapshot extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private Integer reviewPoints = 0;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal reviewSpending = BigDecimal.ZERO;

    @Column(nullable = false)
    private Long reviewVisits = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_before_id")
    private MembershipTier tierBefore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_after_id")
    private MembershipTier tierAfter;

    @Column(nullable = false)
    private LocalDateTime reviewedAt;
}
