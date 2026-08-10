package com.shinecraft.server.booking;

import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyService;
import com.shinecraft.server.loyalty.Reward;
import com.shinecraft.server.loyalty.RewardRedemption;
import com.shinecraft.server.loyalty.RewardRedemptionRepository;
import com.shinecraft.server.loyalty.RewardRedemptionStatus;
import com.shinecraft.server.loyalty.RewardType;
import com.shinecraft.server.promotion.DiscountType;
import com.shinecraft.server.promotion.Promotion;
import com.shinecraft.server.promotion.PromotionService;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingServiceLayer {
    private static final LocalTime OPEN_TIME = LocalTime.of(8, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(17, 0);
    private static final int SLOT_MINUTES = 30;
    private static final List<BookingStatus> OCCUPIED_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_QUEUE, BookingStatus.IN_PROGRESS);

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final CarWashServiceRepository serviceRepository;
    private final RewardRedemptionRepository redemptionRepository;
    private final LoyaltyService loyaltyService;
    private final PromotionService promotionService;
    private final AuthService authService;
    private final int pointsAmountUnit;

    public BookingServiceLayer(
            BookingRepository bookingRepository,
            VehicleRepository vehicleRepository,
            CarWashServiceRepository serviceRepository,
            RewardRedemptionRepository redemptionRepository,
            LoyaltyService loyaltyService,
            PromotionService promotionService,
            AuthService authService,
            @Value("${app.loyalty.points-amount-unit:10000}") int pointsAmountUnit) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceRepository = serviceRepository;
        this.redemptionRepository = redemptionRepository;
        this.loyaltyService = loyaltyService;
        this.promotionService = promotionService;
        this.authService = authService;
        this.pointsAmountUnit = pointsAmountUnit;
    }

    @Transactional
    public BookingDtos.BookingResponse create(BookingDtos.CreateBookingRequest request) {
        User customer = authService.currentUser();
        Vehicle vehicle = vehicleRepository
                .findByIdAndCustomer(request.vehicleId(), customer)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer vehicle not found"));
        LoyaltyAccount account = loyaltyService.getOrCreateAccount(customer);
        validateBookingWindow(request.scheduledAt(), account);
        validateBookableSlot(request.scheduledAt());

        List<CarWashService> selectedServices = serviceRepository.findAllById(request.serviceIds());
        if (selectedServices.size() != request.serviceIds().size() || selectedServices.stream().anyMatch(s -> !s.isActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selected services are invalid");
        }

        BigDecimal subtotal = selectedServices.stream()
                .map(CarWashService::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = BigDecimal.ZERO;

        if (account.getMembershipTier() != null && account.getMembershipTier().getDiscountPercent() != null) {
            discount = discount.add(percent(subtotal, account.getMembershipTier().getDiscountPercent()));
        }

        Promotion promotion = null;
        if (request.promotionId() != null) {
            promotion = promotionService.claimUsable(request.promotionId(), account);
            discount = discount.add(discountForPromotion(subtotal.subtract(discount), promotion));
        }

        RewardRedemption redemption = null;
        if (request.rewardRedemptionId() != null) {
            redemption = redemptionRepository
                    .findByIdAndCustomerAndStatus(
                            request.rewardRedemptionId(), customer, RewardRedemptionStatus.AVAILABLE)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Reward redemption is not available"));
            discount = discount.add(discountForReward(subtotal.subtract(discount), redemption.getReward()));
            redemption.setStatus(RewardRedemptionStatus.USED);
            redemption.setUsedAt(LocalDateTime.now());
        }

        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal finalAmount = subtotal.subtract(discount).max(BigDecimal.ZERO);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setVehicle(vehicle);
        booking.setScheduledAt(request.scheduledAt());
        booking.setStatus(BookingStatus.PENDING);
        booking.setSubtotalAmount(subtotal);
        booking.setDiscountAmount(discount);
        booking.setFinalAmount(finalAmount);
        booking.setPromotion(promotion);
        booking.setRewardRedemption(redemption);
        booking.setNote(request.note());
        selectedServices.forEach(service -> {
            BookingService item = new BookingService();
            item.setService(service);
            item.setServiceName(service.getName());
            item.setPrice(service.getPrice());
            item.setDurationMinutes(service.getDurationMinutes());
            booking.addService(item);
        });

        return BookingDtos.BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.BookingResponse> myBookings() {
        return bookingRepository.findByCustomerOrderByScheduledAtDesc(authService.currentUser()).stream()
                .map(BookingDtos.BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.BookingResponse> todayBookings() {
        LocalDate today = LocalDate.now();
        return bookingRepository
                .findByScheduledAtBetweenOrderByScheduledAtAsc(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream()
                .map(BookingDtos.BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDtos.AvailabilityResponse availability(LocalDate date) {
        User customer = authService.currentUser();
        LoyaltyAccount account = loyaltyService.getOrCreateAccount(customer);
        int bookingWindowDays = account.getMembershipTier() == null ? 7 : account.getMembershipTier().getBookingWindowDays();
        Set<LocalDateTime> occupiedSlots = bookingRepository
                .findByScheduledAtBetweenOrderByScheduledAtAsc(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .filter(booking -> OCCUPIED_STATUSES.contains(booking.getStatus()))
                .map(Booking::getScheduledAt)
                .collect(Collectors.toSet());

        List<BookingDtos.SlotResponse> slots = Stream.iterate(date.atTime(OPEN_TIME), time -> time.plusMinutes(SLOT_MINUTES))
                .limit(slotCount())
                .map(slot -> {
                    String reason = slotReason(slot, bookingWindowDays, occupiedSlots);
                    return new BookingDtos.SlotResponse(slot, reason == null, reason);
                })
                .toList();
        return new BookingDtos.AvailabilityResponse(date.toString(), bookingWindowDays, slots);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.QueueItemResponse> priorityQueue() {
        List<BookingStatus> statuses = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_QUEUE, BookingStatus.IN_PROGRESS);
        return bookingRepository.findByStatusInOrderByScheduledAtAsc(statuses).stream()
                .map(booking -> {
                    LoyaltyAccount account = loyaltyService.getOrCreateAccount(booking.getCustomer());
                    Integer priority = account.getMembershipTier() == null ? 0 : account.getMembershipTier().getPriorityLevel();
                    String tierName = account.getMembershipTier() == null ? "Member" : account.getMembershipTier().getName();
                    return new BookingDtos.QueueItemResponse(
                            booking.getId(),
                            booking.getScheduledAt(),
                            booking.getCustomer().getFullName(),
                            booking.getVehicle().getLicensePlate(),
                            tierName,
                            priority,
                            booking.getStatus(),
                            booking.getFinalAmount());
                })
                .sorted(Comparator.comparing(BookingDtos.QueueItemResponse::priorityLevel)
                        .reversed()
                        .thenComparing(BookingDtos.QueueItemResponse::scheduledAt))
                .toList();
    }

    @Transactional
    public BookingDtos.BookingResponse updateStatus(Long bookingId, BookingStatus status) {
        Booking booking = bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        booking.setStatus(status);
        if (status == BookingStatus.COMPLETED && booking.getCompletedAt() == null) {
            booking.setCompletedAt(LocalDateTime.now());
            int points = booking.getFinalAmount()
                    .divide(BigDecimal.valueOf(pointsAmountUnit), 0, RoundingMode.DOWN)
                    .intValue();
            booking.setEarnedPoints(points);
            if (points > 0) {
                loyaltyService.earnPoints(
                        booking.getCustomer(),
                        booking.getFinalAmount(),
                        points,
                        "Earned points from booking #" + booking.getId(),
                        booking);
            }
        }
        return BookingDtos.BookingResponse.from(booking);
    }

    private void validateBookingWindow(LocalDateTime scheduledAt, LoyaltyAccount account) {
        int windowDays = account.getMembershipTier() == null ? 7 : account.getMembershipTier().getBookingWindowDays();
        if (scheduledAt.isAfter(LocalDateTime.now().plusDays(windowDays))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Current membership tier can only book up to " + windowDays + " days in advance");
        }
    }

    private void validateBookableSlot(LocalDateTime scheduledAt) {
        if (!isAlignedSlot(scheduledAt.toLocalTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking slot must be between 08:00 and 17:00 and aligned to 30-minute intervals");
        }
        if (bookingRepository.existsByScheduledAtAndStatusIn(scheduledAt, OCCUPIED_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "This booking slot is already reserved");
        }
    }

    private String slotReason(LocalDateTime slot, int bookingWindowDays, Set<LocalDateTime> occupiedSlots) {
        LocalDateTime now = LocalDateTime.now();
        if (!slot.isAfter(now)) {
            return "PAST";
        }
        if (slot.isAfter(now.plusDays(bookingWindowDays))) {
            return "OUT_OF_TIER_WINDOW";
        }
        if (occupiedSlots.contains(slot)) {
            return "BOOKED";
        }
        return null;
    }

    private boolean isAlignedSlot(LocalTime time) {
        return !time.isBefore(OPEN_TIME)
                && time.isBefore(CLOSE_TIME)
                && time.getSecond() == 0
                && time.getNano() == 0
                && time.getMinute() % SLOT_MINUTES == 0;
    }

    private long slotCount() {
        return java.time.Duration.between(OPEN_TIME, CLOSE_TIME).toMinutes() / SLOT_MINUTES;
    }

    private BigDecimal discountForPromotion(BigDecimal base, Promotion promotion) {
        if (promotion.getDiscountType() == DiscountType.PERCENTAGE) {
            return percent(base, promotion.getDiscountValue());
        }
        return promotion.getDiscountValue().min(base);
    }

    private BigDecimal discountForReward(BigDecimal base, Reward reward) {
        if (reward.getRewardType() == RewardType.FREE_WASH) {
            return base;
        }
        if (reward.getRewardType() == RewardType.DISCOUNT_CODE && reward.getDiscountAmount() != null) {
            return reward.getDiscountAmount().min(base);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
