package com.shinecraft.server.booking;

import com.shinecraft.server.audit.AuditTrailService;
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
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingServiceLayer {
    private static final LocalTime OPEN_TIME = LocalTime.of(8, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(17, 0);
    private static final int SLOT_MINUTES = 30;
    private static final List<BookingStatus> OCCUPIED_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_QUEUE, BookingStatus.IN_PROGRESS);
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            BookingStatus.PENDING, Set.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.IN_QUEUE, BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED),
            BookingStatus.IN_QUEUE, Set.of(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED),
            BookingStatus.IN_PROGRESS, Set.of(BookingStatus.COMPLETED, BookingStatus.CANCELLED),
            BookingStatus.COMPLETED, Set.of(),
            BookingStatus.CANCELLED, Set.of());
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final CarWashServiceRepository serviceRepository;
    private final RewardRedemptionRepository redemptionRepository;
    private final LoyaltyService loyaltyService;
    private final PromotionService promotionService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final AuditTrailService auditTrailService;
    private final int pointsAmountUnit;

    public BookingServiceLayer(
            BookingRepository bookingRepository,
            VehicleRepository vehicleRepository,
            CarWashServiceRepository serviceRepository,
            RewardRedemptionRepository redemptionRepository,
            LoyaltyService loyaltyService,
            PromotionService promotionService,
            AuthService authService,
            UserRepository userRepository,
            AuditTrailService auditTrailService,
            @Value("${app.loyalty.points-amount-unit:10000}") int pointsAmountUnit) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceRepository = serviceRepository;
        this.redemptionRepository = redemptionRepository;
        this.loyaltyService = loyaltyService;
        this.promotionService = promotionService;
        this.authService = authService;
        this.userRepository = userRepository;
        this.auditTrailService = auditTrailService;
        this.pointsAmountUnit = pointsAmountUnit;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingDtos.BookingResponse create(BookingDtos.CreateBookingRequest request) {
        User customer = authService.currentUser();
        Vehicle vehicle = vehicleRepository
                .findByIdAndCustomer(request.vehicleId(), customer)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer vehicle not found"));
        LoyaltyAccount account = loyaltyService.getOrCreateAccount(customer);
        validateBookingWindow(request.scheduledAt(), account);
        validateBookableSlot(request.scheduledAt());

        List<Long> requestedServiceIds = request.resolvedServiceIds();
        if (requestedServiceIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one service is required");
        }
        List<CarWashService> selectedServices = new ArrayList<>(serviceRepository.findAllById(requestedServiceIds));
        if (selectedServices.size() != requestedServiceIds.size() || selectedServices.stream().anyMatch(s -> !s.isActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selected services are invalid");
        }

        RewardRedemption redemption = null;
        String redemptionBeforeValue = null;
        CarWashService freeAddOnService = null;
        if (request.rewardRedemptionId() != null) {
            redemption = redemptionRepository
                    .findByIdAndCustomerAndStatus(
                            request.rewardRedemptionId(), customer, RewardRedemptionStatus.AVAILABLE)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Reward redemption is not available"));
            redemptionBeforeValue = rewardRedemptionAuditValue(redemption);
            if (redemption.getExpiresAt() != null && !redemption.getExpiresAt().isAfter(LocalDateTime.now())) {
                loyaltyService.markRedemptionExpired(redemption.getId());
                throw new ApiException(HttpStatus.BAD_REQUEST, "Reward redemption has expired");
            }
            Reward reward = redemption.getReward();
            if (reward.getRewardType() == RewardType.ADD_ON) {
                CarWashService addOn = requireActiveAddOnService(reward);
                boolean alreadySelected = selectedServices.stream().anyMatch(service -> service.getId().equals(addOn.getId()));
                if (!alreadySelected) {
                    freeAddOnService = addOn;
                }
            }
        }

        List<CarWashService> bookedServices = new ArrayList<>(selectedServices);
        if (freeAddOnService != null) {
            bookedServices.add(freeAddOnService);
        }
        int requiredSlots = slotsForDuration(totalDuration(bookedServices));
        validateBookingEndTime(request.scheduledAt(), requiredSlots);
        validateNoOverlappingBooking(request.scheduledAt(), requiredSlots);

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

        if (redemption != null) {
            Reward reward = redemption.getReward();
            validateRewardOrderMinimum(subtotal, reward);
            if (reward.getRewardType() == RewardType.ADD_ON) {
                CarWashService addOn = requireActiveAddOnService(reward);
                boolean alreadySelected = selectedServices.stream().anyMatch(service -> service.getId().equals(addOn.getId()));
                if (alreadySelected) {
                    discount = discount.add(limitRewardDiscount(addOn.getPrice().min(subtotal.subtract(discount)), reward));
                }
            } else {
                discount = discount.add(discountForReward(subtotal.subtract(discount), reward));
            }
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
        selectedServices.forEach(service -> addBookingService(booking, service, service.getPrice()));
        if (freeAddOnService != null) {
            addBookingService(booking, freeAddOnService, BigDecimal.ZERO);
        }

        Booking savedBooking = bookingRepository.save(booking);
        auditTrailService.record(customer, customer, "BOOKING_CREATED", null, bookingAuditValue(savedBooking));
        if (redemption != null) {
            auditTrailService.record(
                    customer,
                    customer,
                    "REWARD_REDEMPTION_USED",
                    redemptionBeforeValue,
                    rewardRedemptionAuditValue(redemption));
        }
        if (promotion != null) {
            promotionService.recordPromotionUsed(promotion, savedBooking.getCustomer(), savedBooking.getId());
        }
        return BookingDtos.BookingResponse.from(savedBooking);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingDtos.AppointmentResponse createAppointment(BookingDtos.CreateBookingRequest request) {
        BookingDtos.BookingResponse created = create(request);
        return appointmentDetail(created.id());
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.BookingResponse> myBookings() {
        return bookingRepository.findByCustomerOrderByScheduledAtDesc(authService.currentUser()).stream()
                .map(BookingDtos.BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.AppointmentResponse> myAppointments() {
        return bookingRepository.findByCustomerOrderByScheduledAtDesc(authService.currentUser()).stream()
                .map(BookingDtos.AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.AppointmentResponse> myStaffAppointments() {
        User staff = authService.currentUser();
        if (staff.getRole() == UserRole.ROLE_ADMIN) {
            return allAppointments();
        }
        return bookingRepository.findByAssignedStaffOrderByScheduledAtDesc(staff).stream()
                .map(BookingDtos.AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.AppointmentResponse> allAppointments() {
        requireAdmin(authService.currentUser());
        return bookingRepository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getScheduledAt).reversed())
                .map(BookingDtos.AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDtos.AppointmentResponse appointmentDetail(Long bookingId) {
        Booking booking = bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
        requireCanViewBooking(authService.currentUser(), booking);
        return BookingDtos.AppointmentResponse.from(booking);
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
        List<Booking> activeBookings = bookingRepository
                .findByScheduledAtBetweenOrderByScheduledAtAsc(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .filter(booking -> OCCUPIED_STATUSES.contains(booking.getStatus()))
                .toList();

        List<BookingDtos.SlotResponse> slots = Stream.iterate(date.atTime(OPEN_TIME), time -> time.plusMinutes(SLOT_MINUTES))
                .limit(slotCount())
                .map(slot -> {
                    String reason = slotReason(slot, bookingWindowDays, activeBookings);
                    return new BookingDtos.SlotResponse(slot, reason == null, reason);
                })
                .toList();
        return new BookingDtos.AvailabilityResponse(date.toString(), bookingWindowDays, slots);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.QueueItemResponse> priorityQueue() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> bookings = bookingRepository.findByStatusInOrderByScheduledAtAsc(
                List.of(BookingStatus.IN_QUEUE, BookingStatus.IN_PROGRESS));

        List<BookingDtos.QueueItemResponse> inProgress = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.IN_PROGRESS)
                .map(booking -> queueItem(booking, now, null))
                .sorted(Comparator.comparing(BookingDtos.QueueItemResponse::bookingId))
                .toList();

        List<BookingDtos.QueueItemResponse> waiting = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.IN_QUEUE)
                .map(booking -> queueItem(booking, now, null))
                .sorted(waitingQueueComparator())
                .toList();

        List<BookingDtos.QueueItemResponse> positionedWaiting = IntStream.range(0, waiting.size())
                .mapToObj(index -> withPosition(waiting.get(index), index + 1))
                .toList();
        return Stream.concat(inProgress.stream(), positionedWaiting.stream()).toList();
    }

    @Transactional
    public BookingDtos.BookingResponse updateStatus(Long bookingId, BookingStatus status) {
        Booking booking = bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        User actor = authService.currentUser();
        requireCanUpdateStatus(actor, booking, status);
        String beforeValue = bookingAuditValue(booking);
        BookingStatus currentStatus = booking.getStatus();
        validateStatusTransition(currentStatus, status);
        if (currentStatus == BookingStatus.CONFIRMED
                && (status == BookingStatus.IN_QUEUE || status == BookingStatus.IN_PROGRESS)
                && booking.getCheckInAt() == null) {
            booking.setCheckInAt(LocalDateTime.now());
        }
        if (status == BookingStatus.CANCELLED) {
            restoreCancellationResources(booking);
        }
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
        auditTrailService.record(
                actor,
                booking.getCustomer(),
                "BOOKING_STATUS_CHANGED",
                beforeValue,
                bookingAuditValue(booking));
        return BookingDtos.BookingResponse.from(booking);
    }

    @Transactional
    public BookingDtos.AppointmentResponse updateAppointmentStatus(Long bookingId, BookingStatus status) {
        updateStatus(bookingId, status);
        return appointmentDetail(bookingId);
    }

    @Transactional
    public BookingDtos.PaymentResponse createPayment(Long bookingId, BookingDtos.CreatePaymentRequest request) {
        Booking booking = findBooking(bookingId);
        User actor = authService.currentUser();
        requireCanCreatePayment(actor, booking);
        String beforeValue = bookingAuditValue(booking);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cancelled appointments cannot be paid");
        }
        BookingPaymentMethod method = request.resolvedMethod();
        booking.setPaymentMethod(method);
        booking.setPaymentStatus(BookingPaymentStatus.PENDING);
        booking.setPaidAt(null);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        String paymentId = "APPT-" + booking.getId() + "-" + System.currentTimeMillis();
        String paymentUrl = "/appointments/" + booking.getId() + "/payment/confirm?paymentId=" + paymentId;
        auditTrailService.record(
                actor,
                booking.getCustomer(),
                "PAYMENT_CREATED",
                beforeValue,
                bookingAuditValue(booking));
        return new BookingDtos.PaymentResponse(
                paymentUrl,
                paymentId,
                method.name().toLowerCase(java.util.Locale.ROOT),
                booking.getFinalAmount(),
                expiresAt);
    }

    @Transactional
    public BookingDtos.AppointmentResponse updatePaymentStatus(
            Long bookingId, BookingDtos.UpdatePaymentStatusRequest request) {
        Booking booking = findBooking(bookingId);
        User actor = authService.currentUser();
        requireCanUpdatePayment(actor, booking);
        String beforeValue = bookingAuditValue(booking);
        BookingPaymentStatus status = request == null
                ? BookingPaymentStatus.PAID
                : request.resolvedPaymentStatus();
        BookingPaymentMethod method = request == null
                ? booking.getPaymentMethod()
                : request.resolvedPaymentMethod(booking.getPaymentMethod());
        booking.setPaymentMethod(method);
        booking.setPaymentStatus(status);
        booking.setPaidAt(status == BookingPaymentStatus.PAID ? LocalDateTime.now() : null);
        auditTrailService.record(
                actor,
                booking.getCustomer(),
                "PAYMENT_STATUS_CHANGED",
                beforeValue,
                bookingAuditValue(booking));
        return BookingDtos.AppointmentResponse.from(booking);
    }

    @Transactional
    public BookingDtos.AppointmentResponse assignStaff(Long bookingId, BookingDtos.AssignStaffRequest request) {
        Booking booking = findBooking(bookingId);
        User actor = authService.currentUser();
        requireAdmin(actor);
        String beforeValue = bookingAuditValue(booking);
        User staff = userRepository
                .findById(request.staffId())
                .filter(user -> user.isActive() && user.getRole() == UserRole.ROLE_STAFF)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Assigned staff is not available"));
        booking.setAssignedStaff(staff);
        auditTrailService.record(
                actor,
                booking.getCustomer(),
                "STAFF_ASSIGNED",
                beforeValue,
                bookingAuditValue(booking));
        return BookingDtos.AppointmentResponse.from(booking);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingDtos.AppointmentResponse reschedule(Long bookingId, BookingDtos.RescheduleRequest request) {
        Booking booking = findBooking(bookingId);
        User actor = authService.currentUser();
        requireAdmin(actor);
        String beforeValue = bookingAuditValue(booking);
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Completed or cancelled appointments cannot be rescheduled");
        }
        int requiredSlots = slotsForDuration(totalDuration(booking));
        validateBookingEndTime(request.scheduledAt(), requiredSlots);
        validateNoOverlappingBooking(request.scheduledAt(), requiredSlots, booking.getId());
        booking.setScheduledAt(request.scheduledAt());
        auditTrailService.record(
                actor,
                booking.getCustomer(),
                "BOOKING_RESCHEDULED",
                beforeValue,
                bookingAuditValue(booking));
        return BookingDtos.AppointmentResponse.from(booking);
    }

    private void validateStatusTransition(BookingStatus currentStatus, BookingStatus requestedStatus) {
        if (!ALLOWED_STATUS_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(requestedStatus)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid booking status transition from " + currentStatus + " to " + requestedStatus);
        }
    }

    private void requireAdmin(User actor) {
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin permission is required");
        }
    }

    private void requireCanViewBooking(User actor, Booking booking) {
        if (actor.getRole() == UserRole.ROLE_ADMIN
                || sameUser(actor, booking.getCustomer())
                || sameUser(actor, booking.getAssignedStaff())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to access this appointment");
    }

    private void requireCanUpdateStatus(User actor, Booking booking, BookingStatus requestedStatus) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (actor.getRole() == UserRole.ROLE_STAFF && sameUser(actor, booking.getAssignedStaff())) {
            if (requestedStatus == BookingStatus.CONFIRMED
                    || requestedStatus == BookingStatus.IN_PROGRESS
                    || requestedStatus == BookingStatus.COMPLETED) {
                return;
            }
        }
        if (actor.getRole() == UserRole.ROLE_CUSTOMER
                && sameUser(actor, booking.getCustomer())
                && requestedStatus == BookingStatus.CANCELLED) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to update this appointment status");
    }

    private void requireCanCreatePayment(User actor, Booking booking) {
        if (actor.getRole() == UserRole.ROLE_ADMIN
                || sameUser(actor, booking.getCustomer())
                || sameUser(actor, booking.getAssignedStaff())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to create payment for this appointment");
    }

    private void requireCanUpdatePayment(User actor, Booking booking) {
        if (actor.getRole() == UserRole.ROLE_ADMIN || sameUser(actor, booking.getAssignedStaff())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to update this appointment payment");
    }

    private boolean sameUser(User first, User second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getId() != null && second.getId() != null) {
            return first.getId().equals(second.getId());
        }
        return first == second;
    }

    private void restoreCancellationResources(Booking booking) {
        if (booking.getPromotion() != null) {
            Promotion promotion = booking.getPromotion();
            int usedCountBefore = promotion.getUsedCount();
            promotionService.restoreUsage(promotion);
            promotionService.recordPromotionRestored(
                    promotion, booking.getCustomer(), booking.getId(), usedCountBefore);
        }
        if (booking.getRewardRedemption() != null) {
            RewardRedemption redemption = booking.getRewardRedemption();
            if (redemption.getExpiresAt() != null && redemption.getExpiresAt().isBefore(LocalDateTime.now())) {
                redemption.setStatus(RewardRedemptionStatus.EXPIRED);
            } else {
                redemption.setStatus(RewardRedemptionStatus.AVAILABLE);
                redemption.setUsedAt(null);
            }
        }
    }

    private BookingDtos.QueueItemResponse queueItem(Booking booking, LocalDateTime now, Integer position) {
        LoyaltyAccount account = loyaltyService.getOrCreateAccount(booking.getCustomer());
        Integer priority = account.getMembershipTier() == null ? 0 : account.getMembershipTier().getPriorityLevel();
        String tierName = account.getMembershipTier() == null ? "Member" : account.getMembershipTier().getName();
        LocalDateTime checkInAt = booking.getCheckInAt();
        Long waitingMinutes = booking.getStatus() == BookingStatus.IN_QUEUE && checkInAt != null
                ? Duration.between(checkInAt, now).toMinutes()
                : null;
        return new BookingDtos.QueueItemResponse(
                booking.getId(),
                booking.getScheduledAt(),
                booking.getCustomer().getFullName(),
                booking.getVehicle().getLicensePlate(),
                tierName,
                priority,
                booking.getStatus(),
                booking.getFinalAmount(),
                checkInAt,
                waitingMinutes,
                totalDuration(booking),
                position);
    }

    private Comparator<BookingDtos.QueueItemResponse> waitingQueueComparator() {
        return Comparator.comparing(BookingDtos.QueueItemResponse::priorityLevel, Comparator.reverseOrder())
                .thenComparing(item -> item.checkInAt() == null)
                .thenComparing(
                        BookingDtos.QueueItemResponse::checkInAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BookingDtos.QueueItemResponse::serviceDurationMinutes)
                .thenComparing(BookingDtos.QueueItemResponse::bookingId);
    }

    private BookingDtos.QueueItemResponse withPosition(BookingDtos.QueueItemResponse item, int position) {
        return new BookingDtos.QueueItemResponse(
                item.bookingId(),
                item.scheduledAt(),
                item.customerName(),
                item.licensePlate(),
                item.tierName(),
                item.priorityLevel(),
                item.status(),
                item.finalAmount(),
                item.checkInAt(),
                item.waitingMinutes(),
                item.serviceDurationMinutes(),
                position);
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
        if (!isBookableStartTime(scheduledAt.toLocalTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking start time must be between 08:00 and 17:00 with minute precision");
        }
        if (bookingRepository.existsByScheduledAtAndStatusIn(scheduledAt, OCCUPIED_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "This booking slot is already reserved");
        }
    }

    private String slotReason(LocalDateTime slot, int bookingWindowDays, List<Booking> activeBookings) {
        LocalDateTime now = LocalDateTime.now();
        if (!slot.isAfter(now)) {
            return "PAST";
        }
        if (slot.isAfter(now.plusDays(bookingWindowDays))) {
            return "OUT_OF_TIER_WINDOW";
        }
        if (activeBookings.stream().anyMatch(booking -> overlaps(slot, endAt(slot, 1), booking.getScheduledAt(), endAt(booking)))) {
            return "BOOKED";
        }
        return null;
    }

    private void validateBookingEndTime(LocalDateTime scheduledAt, int requiredSlots) {
        LocalDateTime endAt = endAt(scheduledAt, requiredSlots);
        if (endAt.isAfter(scheduledAt.toLocalDate().atTime(CLOSE_TIME))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking duration must end by 17:00");
        }
    }

    private void validateNoOverlappingBooking(LocalDateTime scheduledAt, int requiredSlots) {
        validateNoOverlappingBooking(scheduledAt, requiredSlots, null);
    }

    private void validateNoOverlappingBooking(LocalDateTime scheduledAt, int requiredSlots, Long ignoredBookingId) {
        LocalDateTime endAt = endAt(scheduledAt, requiredSlots);
        boolean overlaps = bookingRepository
                .findByScheduledAtBetweenOrderByScheduledAtAsc(
                        scheduledAt.toLocalDate().atStartOfDay(), scheduledAt.toLocalDate().plusDays(1).atStartOfDay())
                .stream()
                .filter(existing -> ignoredBookingId == null || !ignoredBookingId.equals(existing.getId()))
                .filter(existing -> OCCUPIED_STATUSES.contains(existing.getStatus()))
                .anyMatch(existing -> overlaps(scheduledAt, endAt, existing.getScheduledAt(), endAt(existing)));
        if (overlaps) {
            throw new ApiException(HttpStatus.CONFLICT, "This booking slot overlaps an existing booking");
        }
    }

    private Booking findBooking(Long bookingId) {
        return bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    private String bookingAuditValue(Booking booking) {
        return auditTrailService.bookingValue(
                booking.getId(),
                booking.getStatus().name(),
                booking.getPaymentStatus().name(),
                booking.getPaymentMethod().name(),
                booking.getAssignedStaff() == null ? null : booking.getAssignedStaff().getId(),
                booking.getScheduledAt() == null ? null : booking.getScheduledAt().toString(),
                booking.getCheckInAt() == null ? null : booking.getCheckInAt().toString(),
                booking.getCompletedAt() == null ? null : booking.getCompletedAt().toString());
    }

    private String rewardRedemptionAuditValue(RewardRedemption redemption) {
        return auditTrailService.rewardRedemptionValue(
                redemption.getId(),
                redemption.getReward().getId(),
                redemption.getCustomer().getId(),
                redemption.getStatus().name(),
                redemption.getUsedAt() == null ? null : redemption.getUsedAt().toString());
    }

    private boolean overlaps(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime existingStartAt, LocalDateTime existingEndAt) {
        return startAt.isBefore(existingEndAt) && existingStartAt.isBefore(endAt);
    }

    private LocalDateTime endAt(LocalDateTime startAt, int requiredSlots) {
        return startAt.plusMinutes((long) requiredSlots * SLOT_MINUTES);
    }

    private int slotsForDuration(int durationMinutes) {
        return Math.max(1, (durationMinutes + SLOT_MINUTES - 1) / SLOT_MINUTES);
    }

    private int totalDuration(List<CarWashService> services) {
        return services.stream().mapToInt(CarWashService::getDurationMinutes).sum();
    }

    private int totalDuration(Booking booking) {
        return booking.getServices().stream().mapToInt(BookingService::getDurationMinutes).sum();
    }

    private LocalDateTime endAt(Booking booking) {
        return endAt(booking.getScheduledAt(), slotsForDuration(totalDuration(booking)));
    }

    private boolean isBookableStartTime(LocalTime time) {
        return !time.isBefore(OPEN_TIME)
                && time.isBefore(CLOSE_TIME)
                && time.getSecond() == 0
                && time.getNano() == 0;
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
        BigDecimal discount = BigDecimal.ZERO;
        if (reward.getRewardType() == RewardType.FREE_WASH) {
            discount = base;
        } else if (reward.getRewardType() == RewardType.DISCOUNT_CODE && reward.getDiscountAmount() != null) {
            discount = reward.getDiscountAmount().min(base);
        }
        return limitRewardDiscount(discount, reward);
    }

    private void validateRewardOrderMinimum(BigDecimal subtotal, Reward reward) {
        if (reward.getMinOrderAmount() != null && subtotal.compareTo(reward.getMinOrderAmount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reward requires a higher order amount");
        }
    }

    private BigDecimal limitRewardDiscount(BigDecimal discount, Reward reward) {
        if (reward.getMaxDiscountAmount() != null) {
            return discount.min(reward.getMaxDiscountAmount());
        }
        return discount;
    }

    private CarWashService requireActiveAddOnService(Reward reward) {
        CarWashService addOn = reward.getAddOnService();
        if (addOn == null || !addOn.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reward add-on service is not available");
        }
        return addOn;
    }

    private void addBookingService(Booking booking, CarWashService service, BigDecimal price) {
        BookingService item = new BookingService();
        item.setService(service);
        item.setServiceName(service.getName());
        item.setPrice(price);
        item.setDurationMinutes(service.getDurationMinutes());
        booking.addService(item);
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
