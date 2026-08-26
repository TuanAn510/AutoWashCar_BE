package com.shinecraft.server.booking;

import com.shinecraft.server.common.BaseEntity;
import com.shinecraft.server.loyalty.RewardRedemption;
import com.shinecraft.server.promotion.Promotion;
import com.shinecraft.server.user.User;
import com.shinecraft.server.vehicle.Vehicle;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private User assignedStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secondary_assigned_staff_id")
    private User secondaryAssignedStaff;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "check_in_at")
    private LocalDateTime checkInAt;

    @Column(name = "service_started_at")
    private LocalDateTime serviceStartedAt;

    @Column(name = "check_in_image_url", length = 500)
    private String checkInImageUrl;

    @Column(name = "completion_image_url", length = 500)
    private String completionImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(nullable = false)
    private Integer earnedPoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingPaymentMethod paymentMethod = BookingPaymentMethod.CASH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingPaymentStatus paymentStatus = BookingPaymentStatus.UNPAID;

    private LocalDateTime paidAt;

    @Column(length = 100)
    private String paymentGatewayRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 40)
    private BookingCancellationReason cancellationReason;

    @Column(name = "refund_required", nullable = false)
    private boolean refundRequired = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_redemption_id")
    private RewardRedemption rewardRedemption;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String note;

    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingService> services = new ArrayList<>();

    public void addService(BookingService service) {
        service.setBooking(this);
        services.add(service);
    }
}
