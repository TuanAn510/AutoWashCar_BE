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
import com.shinecraft.server.payment.VnPayService;
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
import java.util.TreeMap;
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
    private static final int AVAILABILITY_SUGGESTION_MINUTES = 15;
    private static final int DEFAULT_AVAILABILITY_DURATION_MINUTES = 30;
    private static final int MINIMUM_LEAD_TIME_MINUTES = 30;
    private static final int SHOP_CONCURRENT_CAPACITY = 2;
    private static final List<BookingStatus> OCCUPIED_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_QUEUE, BookingStatus.IN_PROGRESS);
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            BookingStatus.PENDING, Set.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.IN_QUEUE, BookingStatus.CANCELLED),
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
    private final VnPayService vnPayService;
    private final int pointsAmountUnit;
    private final boolean enforceScheduleTime;

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
            VnPayService vnPayService,
            @Value("${app.booking.enforce-schedule-time:true}") boolean enforceScheduleTime,
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
        this.vnPayService = vnPayService;
        this.pointsAmountUnit = pointsAmountUnit;
        this.enforceScheduleTime = enforceScheduleTime;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingDtos.BookingResponse create(BookingDtos.CreateBookingRequest request) {
        User customer = authService.currentUser();
        Vehicle vehicle = vehicleRepository
                .findByIdAndCustomer(request.vehicleId(), customer)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer vehicle not found"));
        if (vehicle.getVerificationStatus() != com.shinecraft.server.vehicle.VehicleVerificationStatus.APPROVED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle brand/model verification must be approved before booking");
        }
        LoyaltyAccount account = loyaltyService.getOrCreateAccount(customer);
        validateMinimumLeadTime(request.scheduledAt());
        validateBookingWindow(request.scheduledAt(), account);
        validateBookableStartTime(request.scheduledAt());

        List<Long> requestedServiceIds = request.resolvedServiceIds();
        if (requestedServiceIds.size() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Exactly one primary service is required");
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
        int reservationDurationMinutes = totalDuration(bookedServices);
        validateBookingEndTime(request.scheduledAt(), reservationDurationMinutes);
        validateVehicleNoOverlap(vehicle, request.scheduledAt(), reservationDurationMinutes, null);
        validateShopCapacity(request.scheduledAt(), reservationDurationMinutes, null);

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
        return bookingRepository.findByCustomerOrderByCreatedAtDesc(authService.currentUser()).stream()
                .sorted(Comparator.comparing(
                        (Booking b) -> b.getCompletedAt() != null ? b.getCompletedAt() : b.getCreatedAt())
                        .reversed())
                .map(BookingDtos.AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDtos.AppointmentPageResponse myStaffAppointments(BookingDtos.AppointmentFilterParams params) {
        User staff = authService.currentUser();
        List<Booking> bookings;
        if (staff.getRole() == UserRole.ROLE_ADMIN) {
            bookings = bookingRepository.findAllByOrderByScheduledAtDesc();
        } else {
            bookings = bookingRepository.findByAssignedStaffOrSecondaryAssignedStaffOrderByScheduledAtDesc(staff, staff);
        }
        return filterAndPaginateBookings(bookings, params);
    }

    @Transactional(readOnly = true)
    public BookingDtos.AppointmentPageResponse allAppointments(BookingDtos.AppointmentFilterParams params) {
        List<Booking> bookings = bookingRepository.findAllByOrderByScheduledAtDesc();
        return filterAndPaginateBookings(bookings, params);
    }

    private BookingDtos.AppointmentPageResponse filterAndPaginateBookings(
            List<Booking> bookings, BookingDtos.AppointmentFilterParams params) {
        // Filter
        var stream = bookings.stream();
        if (params.search() != null && !params.search().isBlank()) {
            String keyword = params.search().toLowerCase().trim();
            stream = stream.filter(booking -> matchesSearch(booking, keyword));
        }
        if (params.status() != null && !params.status().isBlank()) {
            stream = stream.filter(booking -> matchesStatus(booking, params.status()));
        }
        if (params.staffId() != null && !params.staffId().isBlank()) {
            stream = stream.filter(booking -> isAssignedToStaff(booking, params.staffId()));
        }
        if (params.dateFrom() != null && !params.dateFrom().isBlank()) {
            LocalDateTime dateFrom = LocalDateTime.parse(params.dateFrom());
            stream = stream.filter(booking ->
                    !booking.getScheduledAt().isBefore(dateFrom));
        }
        if (params.dateTo() != null && !params.dateTo().isBlank()) {
            LocalDateTime dateTo = LocalDateTime.parse(params.dateTo());
            stream = stream.filter(booking ->
                    !booking.getScheduledAt().isAfter(dateTo));
        }

        // Sort
        Comparator<Booking> comparator = buildComparator(params.sortBy(), params.sortOrder());
        stream = stream.sorted(comparator);

        List<Booking> filtered = stream.toList();

        // Build summary (before pagination)
        BookingDtos.AppointmentStatusSummary summary = BookingDtos.AppointmentStatusSummary.from(filtered);

        // Paginate
        int page = Math.max(params.page() - 1, 0); // 0-based for internal use
        int limit = Math.min(Math.max(params.limit(), 1), 100);
        int total = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / limit));
        int fromIndex = page * limit;
        int toIndex = Math.min(fromIndex + limit, total);

        List<Booking> paged = fromIndex < total
                ? filtered.subList(fromIndex, toIndex)
                : List.of();

        List<BookingDtos.AppointmentResponse> appointments = paged.stream()
                .map(BookingDtos.AppointmentResponse::from)
                .toList();

        BookingDtos.PaginationMeta pagination = new BookingDtos.PaginationMeta(
                page + 1, limit, total, totalPages);

        return new BookingDtos.AppointmentPageResponse(appointments, pagination, summary);
    }

    private boolean matchesSearch(Booking booking, String keyword) {
        if (booking.getCustomer() != null) {
            if (booking.getCustomer().getFullName() != null
                    && booking.getCustomer().getFullName().toLowerCase().contains(keyword)) {
                return true;
            }
            if (booking.getCustomer().getPhone() != null
                    && booking.getCustomer().getPhone().contains(keyword)) {
                return true;
            }
        }
        if (booking.getVehicle() != null && booking.getVehicle().getLicensePlate() != null
                && booking.getVehicle().getLicensePlate().toLowerCase().contains(keyword)) {
            return true;
        }
        // Match by booking ID
        try {
            if (String.valueOf(booking.getId()).equals(keyword)) {
                return true;
            }
        } catch (NumberFormatException ignored) {
            // keyword is not a number, skip ID match
        }
        return false;
    }

    private boolean matchesStatus(Booking booking, String frontendStatus) {
        return switch (frontendStatus.toLowerCase()) {
            case "pending" -> booking.getStatus() == BookingStatus.PENDING;
            case "confirmed" -> booking.getStatus() == BookingStatus.CONFIRMED
                    || booking.getStatus() == BookingStatus.IN_QUEUE;
            case "in_progress" -> booking.getStatus() == BookingStatus.IN_PROGRESS;
            case "completed" -> booking.getStatus() == BookingStatus.COMPLETED;
            case "cancelled" -> booking.getStatus() == BookingStatus.CANCELLED;
            default -> true; // unknown status, no filter
        };
    }

    private Comparator<Booking> buildComparator(String sortBy, String sortOrder) {
        boolean desc = !"asc".equalsIgnoreCase(sortOrder);
        Comparator<Booking> comparator = switch (sortBy != null ? sortBy : "activity") {
            case "createdAt" -> Comparator.comparing(Booking::getCreatedAt);
            case "scheduledAt" -> Comparator.comparing(Booking::getScheduledAt);
            case "status" -> Comparator.comparing(Booking::getStatus);
            case "finalAmount" -> Comparator.comparing(Booking::getFinalAmount);
            default -> Comparator.comparing(
                    (Booking b) -> b.getCompletedAt() != null ? b.getCompletedAt() : b.getCreatedAt());
        };
        if (desc) {
            comparator = comparator.reversed();
        }
        return comparator;
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
    public BookingDtos.AvailabilityResponse availability(
            LocalDate date, Long vehicleId, Long serviceId, Long rewardRedemptionId) {
        User customer = authService.currentUser();
        LoyaltyAccount account = loyaltyService.getOrCreateAccount(customer);
        int bookingWindowDays = account.getMembershipTier() == null ? 7 : account.getMembershipTier().getBookingWindowDays();
        Vehicle vehicle = vehicleId == null
                ? null
                : vehicleRepository
                        .findByIdAndCustomer(vehicleId, customer)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer vehicle not found"));
        List<CarWashService> requestedServices = resolveAvailabilityServices(serviceId, rewardRedemptionId, customer);
        int reservationDurationMinutes = requestedServices.isEmpty()
                ? DEFAULT_AVAILABILITY_DURATION_MINUTES
                : totalDuration(requestedServices);
        List<Booking> activeBookings = bookingRepository
                .findByScheduledAtBetweenOrderByScheduledAtAsc(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .filter(booking -> OCCUPIED_STATUSES.contains(booking.getStatus()))
                .toList();
        List<Booking> activeVehicleBookings = vehicle == null
                ? List.of()
                : bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(vehicle, OCCUPIED_STATUSES);
        int effectiveCapacity = effectiveShopCapacity();

        List<BookingDtos.SlotResponse> slots = Stream.iterate(
                        date.atTime(OPEN_TIME), time -> time.plusMinutes(AVAILABILITY_SUGGESTION_MINUTES))
                .limit(slotCount())
                .map(slot -> {
                    String reason = slotReason(
                            slot,
                            bookingWindowDays,
                            activeBookings,
                            activeVehicleBookings,
                            reservationDurationMinutes,
                            effectiveCapacity,
                            vehicle != null);
                    return new BookingDtos.SlotResponse(slot, reason == null, reason);
                })
                .toList();
        return new BookingDtos.AvailabilityResponse(date.toString(), bookingWindowDays, slots);
    }

    public BookingDtos.AvailabilityResponse availability(LocalDate date) {
        return availability(date, null, null, null);
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
        if (enforceScheduleTime
                && (status == BookingStatus.IN_PROGRESS || status == BookingStatus.COMPLETED)
                && booking.getScheduledAt().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Không thể bắt đầu hoặc hoàn thành lịch hẹn trước thời gian đã lên lịch");
        }
        if (currentStatus == BookingStatus.CONFIRMED
                    && status == BookingStatus.IN_QUEUE
                    && booking.getCheckInAt() == null) {
            booking.setCheckInAt(LocalDateTime.now());
        }
        if (status == BookingStatus.CANCELLED) {
            restoreCancellationResources(booking);
        }
        booking.setStatus(status);
        if (status == BookingStatus.COMPLETED && booking.getCompletedAt() == null) {
            booking.setCompletedAt(LocalDateTime.now());
            int points = booking.getSubtotalAmount()
                    .divide(BigDecimal.valueOf(pointsAmountUnit), 0, RoundingMode.DOWN)
                    .intValue();

            // Double points if booking includes the 850K service (Chăm Sóc Toàn Diện)
            boolean has850KService = booking.getServices().stream()
                    .anyMatch(bs -> bs.getPrice().compareTo(new BigDecimal("850000")) == 0);
            if (has850KService) {
                points = points * 2;
            }

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
    public BookingDtos.PaymentResponse createPayment(Long bookingId, BookingDtos.CreatePaymentRequest request, String clientIp) {
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

        String paymentUrl;
        String paymentId;
        String qrCodeUrl = null;

        if (method == BookingPaymentMethod.VNPAY) {
            paymentUrl = vnPayService.createPaymentUrl(booking, clientIp);
            paymentId = extractTxnRefFromUrl(paymentUrl);
            booking.setPaymentGatewayRef(paymentId);
        } else {
            // CASH
            paymentId = "APPT-" + booking.getId() + "-" + System.currentTimeMillis();
            paymentUrl = "/appointments/" + booking.getId() + "/payment/confirm?paymentId=" + paymentId;
        }

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
                expiresAt,
                qrCodeUrl);
    }

    @Transactional
    public void confirmPaymentInternal(
            Long bookingId, BookingPaymentStatus status, BookingPaymentMethod method, String gatewayRef) {
        Booking booking = findBooking(bookingId);
        // Only update if not already PAID (prevent duplicate IPN + return)
        if (booking.getPaymentStatus() == BookingPaymentStatus.PAID) {
            return;
        }
        String beforeValue = bookingAuditValue(booking);
        booking.setPaymentMethod(method);
        booking.setPaymentStatus(status);
        booking.setPaymentGatewayRef(gatewayRef);
        booking.setPaidAt(status == BookingPaymentStatus.PAID ? LocalDateTime.now() : null);
        auditTrailService.record(
                null,
                booking.getCustomer(),
                "PAYMENT_CALLBACK_RECEIVED",
                beforeValue,
                bookingAuditValue(booking));
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
        List<User> staffs = resolveAssignableStaffs(request);
        booking.setAssignedStaff(staffs.get(0));
        booking.setSecondaryAssignedStaff(staffs.size() > 1 ? staffs.get(1) : null);
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
        validateMinimumLeadTime(request.scheduledAt());
        validateBookableStartTime(request.scheduledAt());
        int reservationDurationMinutes = totalDuration(booking);
        validateBookingEndTime(request.scheduledAt(), reservationDurationMinutes);
        validateVehicleNoOverlap(booking.getVehicle(), request.scheduledAt(), reservationDurationMinutes, booking.getId());
        validateShopCapacity(request.scheduledAt(), reservationDurationMinutes, booking.getId());
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
                || isAssignedStaff(actor, booking)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to access this appointment");
    }

    private void requireCanUpdateStatus(User actor, Booking booking, BookingStatus requestedStatus) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (actor.getRole() == UserRole.ROLE_STAFF && isAssignedStaff(actor, booking)) {
            if (requestedStatus == BookingStatus.CONFIRMED
                    || requestedStatus == BookingStatus.IN_QUEUE
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
                || isAssignedStaff(actor, booking)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to create payment for this appointment");
    }

    private void requireCanUpdatePayment(User actor, Booking booking) {
        if (actor.getRole() == UserRole.ROLE_ADMIN || isAssignedStaff(actor, booking)) {
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

    private boolean isAssignedStaff(User actor, Booking booking) {
        return sameUser(actor, booking.getAssignedStaff()) || sameUser(actor, booking.getSecondaryAssignedStaff());
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

    private void validateMinimumLeadTime(LocalDateTime scheduledAt) {
        if (scheduledAt.isBefore(LocalDateTime.now().plusMinutes(MINIMUM_LEAD_TIME_MINUTES))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Booking must be scheduled at least 30 minutes in advance");
        }
    }

    private void validateBookableStartTime(LocalDateTime scheduledAt) {
        if (!isBookableStartTime(scheduledAt.toLocalTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking start time must be between 08:00 and 17:00 with minute precision");
        }
    }

    private String slotReason(
            LocalDateTime slot,
            int bookingWindowDays,
            List<Booking> activeBookings,
            List<Booking> activeVehicleBookings,
            int reservationDurationMinutes,
            int effectiveCapacity,
            boolean enforceVehicleRules) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slotEndAt = endAt(slot, reservationDurationMinutes);
        if (!slot.isAfter(now)) {
            return "PAST";
        }
        if (slot.isAfter(now.plusDays(bookingWindowDays))) {
            return "OUT_OF_TIER_WINDOW";
        }
        if (slotEndAt.isAfter(slot.toLocalDate().atTime(CLOSE_TIME))) {
            return "OUT_OF_BUSINESS_HOURS";
        }
        if (effectiveCapacity < 1) {
            return "NO_STAFF";
        }
        if (enforceVehicleRules && overlapsAny(slot, slotEndAt, activeVehicleBookings)) {
            return "VEHICLE_OVERLAP";
        }
        if (wouldExceedShopCapacity(slot, slotEndAt, activeBookings, effectiveCapacity)) {
            return "CAPACITY_FULL";
        }
        return null;
    }

    private void validateBookingEndTime(LocalDateTime scheduledAt, int reservationDurationMinutes) {
        LocalDateTime endAt = endAt(scheduledAt, reservationDurationMinutes);
        if (endAt.isAfter(scheduledAt.toLocalDate().atTime(CLOSE_TIME))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking duration must end by 17:00");
        }
    }

    private void validateVehicleNoOverlap(
            Vehicle vehicle, LocalDateTime scheduledAt, int reservationDurationMinutes, Long ignoredBookingId) {
        LocalDateTime endAt = endAt(scheduledAt, reservationDurationMinutes);
        boolean overlaps = bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(vehicle, OCCUPIED_STATUSES)
                .stream()
                .filter(existing -> ignoredBookingId == null || !ignoredBookingId.equals(existing.getId()))
                .anyMatch(existing -> overlaps(scheduledAt, endAt, existing.getScheduledAt(), endAt(existing)));
        if (overlaps) {
            throw new ApiException(HttpStatus.CONFLICT, "This vehicle already has an appointment in that time range");
        }
    }

    private void validateShopCapacity(
            LocalDateTime scheduledAt, int reservationDurationMinutes, Long ignoredBookingId) {
        LocalDateTime endAt = endAt(scheduledAt, reservationDurationMinutes);
        int effectiveCapacity = effectiveShopCapacity();
        if (effectiveCapacity < 1) {
            throw new ApiException(HttpStatus.CONFLICT, "No active staff is available for booking");
        }
        List<Booking> activeBookings = bookingRepository
                .findByScheduledAtBetweenOrderByScheduledAtAsc(
                        scheduledAt.toLocalDate().atStartOfDay(), scheduledAt.toLocalDate().plusDays(1).atStartOfDay())
                .stream()
                .filter(existing -> ignoredBookingId == null || !ignoredBookingId.equals(existing.getId()))
                .filter(existing -> OCCUPIED_STATUSES.contains(existing.getStatus()))
                .toList();
        if (wouldExceedShopCapacity(scheduledAt, endAt, activeBookings, effectiveCapacity)) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking capacity is full for this time range");
        }
    }

    private Booking findBooking(Long bookingId) {
        return bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    private boolean isAssignedToStaff(Booking booking, String staffId) {
        return Stream.of(booking.getAssignedStaff(), booking.getSecondaryAssignedStaff())
                .filter(staff -> staff != null && staff.getId() != null)
                .anyMatch(staff -> String.valueOf(staff.getId()).equals(staffId));
    }

    private List<User> resolveAssignableStaffs(BookingDtos.AssignStaffRequest request) {
        List<Long> staffIds = request == null ? List.of() : request.resolvedStaffIds();
        if (staffIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one staff must be selected");
        }
        if (staffIds.size() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A booking can have at most 2 assigned staff");
        }

        List<User> staffs = new ArrayList<>();
        for (Long staffId : staffIds) {
            User staff = userRepository
                    .findById(staffId)
                    .filter(user -> user.isActive() && user.getRole() == UserRole.ROLE_STAFF)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Assigned staff is not available"));
            staffs.add(staff);
        }
        return staffs;
    }

    private String bookingAuditValue(Booking booking) {
        return auditTrailService.bookingValue(
                booking.getId(),
                booking.getStatus().name(),
                booking.getPaymentStatus().name(),
                booking.getPaymentMethod().name(),
                booking.getAssignedStaff() == null ? null : booking.getAssignedStaff().getId(),
                booking.getSecondaryAssignedStaff() == null ? null : booking.getSecondaryAssignedStaff().getId(),
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

    private boolean overlapsAny(LocalDateTime startAt, LocalDateTime endAt, List<Booking> bookings) {
        return bookings.stream().anyMatch(booking -> overlaps(startAt, endAt, booking.getScheduledAt(), endAt(booking)));
    }

    private boolean wouldExceedShopCapacity(
            LocalDateTime requestStartAt, LocalDateTime requestEndAt, List<Booking> bookings, int effectiveCapacity) {
        TreeMap<LocalDateTime, Integer> concurrencyChanges = new TreeMap<>();
        bookings.stream()
                .filter(booking -> overlaps(requestStartAt, requestEndAt, booking.getScheduledAt(), endAt(booking)))
                .forEach(booking -> {
                    LocalDateTime overlapStartAt = booking.getScheduledAt().isBefore(requestStartAt)
                            ? requestStartAt
                            : booking.getScheduledAt();
                    LocalDateTime bookingEndAt = endAt(booking);
                    LocalDateTime overlapEndAt = bookingEndAt.isAfter(requestEndAt) ? requestEndAt : bookingEndAt;
                    concurrencyChanges.merge(overlapStartAt, 1, Integer::sum);
                    concurrencyChanges.merge(overlapEndAt, -1, Integer::sum);
                });

        int concurrentBookings = 0;
        for (Map.Entry<LocalDateTime, Integer> change : concurrencyChanges.entrySet()) {
            concurrentBookings += change.getValue();
            if (change.getKey().isBefore(requestEndAt) && concurrentBookings >= effectiveCapacity) {
                return true;
            }
        }
        return false;
    }

    private LocalDateTime endAt(LocalDateTime startAt, int durationMinutes) {
        return startAt.plusMinutes(durationMinutes);
    }

    private int totalDuration(List<CarWashService> services) {
        return services.stream().mapToInt(CarWashService::getDurationMinutes).sum();
    }

    private int totalDuration(Booking booking) {
        return booking.getServices().stream().mapToInt(BookingService::getDurationMinutes).sum();
    }

    private LocalDateTime endAt(Booking booking) {
        return endAt(booking.getScheduledAt(), totalDuration(booking));
    }

    private List<CarWashService> resolveAvailabilityServices(
            Long serviceId, Long rewardRedemptionId, User customer) {
        List<CarWashService> services = new ArrayList<>();
        if (serviceId != null) {
            CarWashService service = serviceRepository
                    .findById(serviceId)
                    .filter(CarWashService::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Selected service is invalid"));
            services.add(service);
        }
        if (rewardRedemptionId != null) {
            RewardRedemption redemption = redemptionRepository
                    .findByIdAndCustomerAndStatus(rewardRedemptionId, customer, RewardRedemptionStatus.AVAILABLE)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Reward redemption is not available"));
            Reward reward = redemption.getReward();
            if (reward.getRewardType() == RewardType.ADD_ON) {
                CarWashService addOn = requireActiveAddOnService(reward);
                if (services.stream().noneMatch(service -> service.getId().equals(addOn.getId()))) {
                    services.add(addOn);
                }
            }
        }
        return services;
    }

    private int effectiveShopCapacity() {
        int activeStaffCount = userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_STAFF).size();
        return Math.min(SHOP_CONCURRENT_CAPACITY, activeStaffCount);
    }

    private boolean isBookableStartTime(LocalTime time) {
        return !time.isBefore(OPEN_TIME)
                && time.isBefore(CLOSE_TIME)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }

    private long slotCount() {
        return java.time.Duration.between(OPEN_TIME, CLOSE_TIME).toMinutes() / AVAILABILITY_SUGGESTION_MINUTES;
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

    private String extractTxnRefFromUrl(String url) {
        if (url == null || !url.contains("vnp_TxnRef=")) return null;
        String[] parts = url.split("vnp_TxnRef=");
        if (parts.length < 2) return null;
        String txnRef = parts[1];
        int ampIndex = txnRef.indexOf("&");
        return ampIndex > 0 ? txnRef.substring(0, ampIndex) : txnRef;
    }
}
