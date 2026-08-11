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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "point_lots")
public class PointLot extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "earn_transaction_id")
    private LoyaltyTransaction earnTransaction;

    @Column(nullable = false)
    private Integer initialPoints;

    @Column(nullable = false)
    private Integer remainingPoints;

    @Column(nullable = false)
    private LocalDateTime earnedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
