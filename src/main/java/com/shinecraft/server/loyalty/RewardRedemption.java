package com.shinecraft.server.loyalty;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reward_redemptions")
public class RewardRedemption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reward_id")
    private Reward reward;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false)
    private Integer pointsUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RewardRedemptionStatus status = RewardRedemptionStatus.AVAILABLE;

    @Column(nullable = false)
    private LocalDateTime redeemedAt;

    private LocalDateTime usedAt;

    private LocalDateTime expiresAt;
}
