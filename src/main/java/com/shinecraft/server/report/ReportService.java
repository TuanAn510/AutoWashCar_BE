package com.shinecraft.server.report;

import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.booking.BookingRepository;
import com.shinecraft.server.booking.BookingStatus;
import com.shinecraft.server.loyalty.LoyaltyTransaction;
import com.shinecraft.server.loyalty.LoyaltyTransactionRepository;
import com.shinecraft.server.loyalty.LoyaltyTransactionType;
import com.shinecraft.server.promotion.PromotionRepository;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PromotionRepository promotionRepository;
    private final com.shinecraft.server.loyalty.RewardRepository rewardRepository;
    private final LoyaltyTransactionRepository transactionRepository;

    public ReportService(
            UserRepository userRepository,
            BookingRepository bookingRepository,
            PromotionRepository promotionRepository,
            com.shinecraft.server.loyalty.RewardRepository rewardRepository,
            LoyaltyTransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.promotionRepository = promotionRepository;
        this.rewardRepository = rewardRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public ReportDtos.DashboardResponse dashboard() {
        List<Booking> bookings = bookingRepository.findAll();
        List<LoyaltyTransaction> transactions = transactionRepository.findAll();
        BigDecimal revenue = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .map(Booking::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int issued = transactions.stream()
                .filter(t -> t.getType() == LoyaltyTransactionType.EARN)
                .mapToInt(LoyaltyTransaction::getPoints)
                .sum();
        int redeemed = transactions.stream()
                .filter(t -> t.getType() == LoyaltyTransactionType.REDEEM)
                .mapToInt(t -> Math.abs(t.getPoints()))
                .sum();
        LocalDateTime now = LocalDateTime.now();
        return new ReportDtos.DashboardResponse(
                userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_CUSTOMER).size(),
                bookings.size(),
                promotionRepository
                        .findByIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(now, now)
                        .size(),
                rewardRepository.findByIsActiveTrueOrderByRequiredPointsAsc().size(),
                revenue,
                issued,
                redeemed);
    }

    @Transactional(readOnly = true)
    public String bookingsCsv() {
        StringBuilder builder = new StringBuilder("booking_id,customer_id,scheduled_at,status,final_amount,earned_points\n");
        for (Booking booking : bookingRepository.findAll()) {
            builder.append(booking.getId())
                    .append(',')
                    .append(booking.getCustomer().getId())
                    .append(',')
                    .append(booking.getScheduledAt())
                    .append(',')
                    .append(booking.getStatus())
                    .append(',')
                    .append(booking.getFinalAmount())
                    .append(',')
                    .append(booking.getEarnedPoints())
                    .append('\n');
        }
        return builder.toString();
    }
}
