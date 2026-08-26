package com.shinecraft.server.report;

import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.booking.BookingPaymentStatus;
import com.shinecraft.server.booking.BookingRepository;
import com.shinecraft.server.booking.BookingService;
import com.shinecraft.server.booking.BookingStatus;
import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyAccountRepository;
import com.shinecraft.server.loyalty.LoyaltyTransaction;
import com.shinecraft.server.loyalty.LoyaltyTransactionRepository;
import com.shinecraft.server.loyalty.LoyaltyTransactionStatus;
import com.shinecraft.server.loyalty.LoyaltyTransactionType;
import com.shinecraft.server.promotion.Promotion;
import com.shinecraft.server.promotion.PromotionRepository;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final String REVENUE_SOURCE = "paid_bookings";

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final CarWashServiceRepository serviceRepository;
    private final PromotionRepository promotionRepository;
    private final com.shinecraft.server.loyalty.RewardRepository rewardRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyTransactionRepository transactionRepository;

    public ReportService(
            UserRepository userRepository,
            BookingRepository bookingRepository,
            VehicleRepository vehicleRepository,
            CarWashServiceRepository serviceRepository,
            PromotionRepository promotionRepository,
            com.shinecraft.server.loyalty.RewardRepository rewardRepository,
            LoyaltyAccountRepository loyaltyAccountRepository,
            LoyaltyTransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceRepository = serviceRepository;
        this.promotionRepository = promotionRepository;
        this.rewardRepository = rewardRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public ReportDtos.DashboardResponse dashboard() {
        List<Booking> bookings = bookingRepository.findAll();
        List<LoyaltyTransaction> transactions = transactionRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        List<Booking> completedBookings = bookings.stream().filter(this::isCompleted).toList();
        List<Booking> paidBookings = bookings.stream().filter(this::isRealizedRevenue).toList();
        return new ReportDtos.DashboardResponse(
                userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_CUSTOMER).size(),
                vehicleRepository.findAll().stream().filter(Vehicle::isActive).count(),
                bookings.size(),
                completedBookings.size(),
                completedBookings.stream().mapToLong(booking -> booking.getServices().size()).sum(),
                promotionRepository
                        .findByIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(now, now)
                        .size(),
                loyaltyAccountRepository.count(),
                rewardRepository.findByIsActiveTrueOrderByRequiredPointsAsc().size(),
                paidBookings.stream()
                        .map(Booking::getFinalAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                sumPoints(transactions, LoyaltyTransactionType.EARN),
                sumPoints(transactions, LoyaltyTransactionType.REDEEM));
    }

    @Transactional(readOnly = true)
    public ReportDtos.RevenueReport revenue(ReportDtos.ReportRange range, String period) {
        List<Booking> bookings = paidBookingsInReportRange(range);
        Map<String, ReportDtos.RevenueReportItem> grouped = new LinkedHashMap<>();
        for (Booking booking : bookings) {
            LocalDateTime date = reportDate(booking);
            String key = groupKey(date, period);
            ReportDtos.RevenueReportItem current = grouped.getOrDefault(
                    key,
                    new ReportDtos.RevenueReportItem(
                            date.getYear(), date.getMonthValue(), key, BigDecimal.ZERO, 0));
            grouped.put(
                    key,
                    new ReportDtos.RevenueReportItem(
                            current.year(),
                            current.month(),
                            current.period(),
                            current.revenue().add(nullToZero(booking.getFinalAmount())),
                            current.completedServicesCount() + booking.getServices().size()));
        }
        return new ReportDtos.RevenueReport(normalizePeriod(period), REVENUE_SOURCE, new ArrayList<>(grouped.values()));
    }

    @Transactional(readOnly = true)
    public ReportDtos.AppointmentReport appointments(ReportDtos.ReportRange range) {
        List<Booking> bookings = bookingsInRange(range);
        long total = bookings.size();
        long completed = bookings.stream().filter(this::isCompleted).count();
        long cancelled = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.CANCELLED).count();
        long pending = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.PENDING
                        || booking.getStatus() == BookingStatus.CONFIRMED)
                .count();

        Map<YearMonth, Long> byMonth = new LinkedHashMap<>();
        bookings.stream()
                .sorted(Comparator.comparing(Booking::getScheduledAt))
                .forEach(booking -> byMonth.merge(YearMonth.from(booking.getScheduledAt()), 1L, Long::sum));
        List<ReportDtos.AppointmentMonthItem> grouped = byMonth.entrySet().stream()
                .map(entry -> new ReportDtos.AppointmentMonthItem(
                        entry.getKey().getYear(),
                        entry.getKey().getMonthValue(),
                        entry.getKey().toString(),
                        entry.getValue()))
                .toList();

        return new ReportDtos.AppointmentReport(
                total,
                completed,
                cancelled,
                pending,
                percentage(completed, total),
                percentage(cancelled, total),
                grouped);
    }

    @Transactional(readOnly = true)
    public ReportDtos.CustomerReport customers(ReportDtos.ReportRange range) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<User> customers = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ROLE_CUSTOMER)
                .toList();
        List<Booking> completedBookings = bookingRepository.findAll().stream().filter(this::isCompleted).toList();
        long returningCustomers = completedBookings.stream()
                .collect(java.util.stream.Collectors.groupingBy(booking -> booking.getCustomer().getId()))
                .values()
                .stream()
                .filter(items -> items.size() >= 2)
                .count();
        return new ReportDtos.CustomerReport(
                customers.size(),
                customers.stream()
                        .filter(user -> createdInRange(user.getCreatedAt(), range))
                        .count(),
                customers.stream()
                        .filter(user -> !user.getCreatedAt().isBefore(monthStart))
                        .count(),
                userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_CUSTOMER).size(),
                returningCustomers);
    }

    @Transactional(readOnly = true)
    public ReportDtos.ServiceReport services(ReportDtos.ReportRange range, int requestedLimit) {
        int limit = Math.max(1, requestedLimit);
        Map<Long, ServiceStats> statsByService = new HashMap<>();
        for (CarWashService service : serviceRepository.findAll()) {
            statsByService.put(
                    service.getId(), new ServiceStats(String.valueOf(service.getId()), service.getName(), 0, BigDecimal.ZERO));
        }
        for (Booking booking : paidBookingsInReportRange(range)) {
            for (BookingService item : booking.getServices()) {
                Long id = item.getService().getId();
                ServiceStats stats = statsByService.computeIfAbsent(
                        id, ignored -> new ServiceStats(String.valueOf(id), item.getServiceName(), 0, BigDecimal.ZERO));
                stats.usageCount += 1;
                stats.revenue = stats.revenue.add(nullToZero(item.getPrice()));
            }
        }
        List<ReportDtos.ServiceReportItem> ranked = statsByService.values().stream()
                .map(ServiceStats::toReportItem)
                .toList();
        List<ReportDtos.ServiceReportItem> most = ranked.stream()
                .sorted(Comparator.comparingLong(ReportDtos.ServiceReportItem::usageCount)
                        .reversed()
                        .thenComparing(ReportDtos.ServiceReportItem::serviceName))
                .limit(limit)
                .toList();
        List<ReportDtos.ServiceReportItem> least = ranked.stream()
                .sorted(Comparator.comparingLong(ReportDtos.ServiceReportItem::usageCount)
                        .thenComparing(ReportDtos.ServiceReportItem::serviceName))
                .limit(limit)
                .toList();
        return new ReportDtos.ServiceReport(limit, most, least);
    }

    @Transactional(readOnly = true)
    public ReportDtos.LoyaltyReport loyalty(ReportDtos.ReportRange range) {
        List<LoyaltyTransaction> transactions = transactionsInRange(range);
        List<LoyaltyAccount> accounts = loyaltyAccountRepository.findAll().stream()
                .filter(account -> account.getCustomer() != null)
                .filter(account -> createdInRange(account.getCustomer().getCreatedAt(), range))
                .toList();
        Map<String, Long> tiers = accounts.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        account -> account.getMembershipTier() == null ? "Chua xep hang" : account.getMembershipTier().getName(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        List<ReportDtos.TierDistributionItem> distribution = tiers.entrySet().stream()
                .map(entry -> new ReportDtos.TierDistributionItem(entry.getKey(), entry.getValue()))
                .toList();
        return new ReportDtos.LoyaltyReport(
                accounts.size(),
                sumPoints(transactions, LoyaltyTransactionType.EARN),
                sumPoints(transactions, LoyaltyTransactionType.REDEEM),
                sumPoints(transactions, LoyaltyTransactionType.EXPIRE),
                distribution);
    }

    @Transactional(readOnly = true)
    public ReportDtos.PromotionReport promotions(ReportDtos.ReportRange range) {
        LocalDateTime now = LocalDateTime.now();
        List<Promotion> promotions = promotionRepository.findAll().stream()
                .filter(promotion -> createdInRange(promotion.getCreatedAt(), range))
                .toList();
        Map<String, Long> byType = promotions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        promotion -> promotion.getDiscountType().name().toLowerCase(Locale.ROOT),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        return new ReportDtos.PromotionReport(
                promotions.size(),
                promotions.stream()
                        .filter(promotion ->
                                promotion.isActive()
                                        && !promotion.getStartAt().isAfter(now)
                                        && !promotion.getEndAt().isBefore(now))
                        .count(),
                promotions.stream().filter(promotion -> promotion.getEndAt().isBefore(now)).count(),
                promotions.stream().mapToLong(Promotion::getUsedCount).sum(),
                byType.entrySet().stream()
                        .map(entry -> new ReportDtos.PromotionDistributionItem(entry.getKey(), entry.getValue()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public ReportDtos.VehicleReport vehicles(ReportDtos.ReportRange range) {
        List<Vehicle> vehicles = vehicleRepository.findAll().stream()
                .filter(vehicle -> vehicle.isActive())
                .filter(vehicle -> createdInRange(vehicle.getCreatedAt(), range))
                .toList();
        Map<String, Long> byBrand = vehicles.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        vehicle -> safeLabel(vehicle.getBrand(), "Unknown"),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        List<ReportDtos.VehicleBrandItem> items = byBrand.entrySet().stream()
                .map(entry -> new ReportDtos.VehicleBrandItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(ReportDtos.VehicleBrandItem::total)
                        .reversed()
                        .thenComparing(ReportDtos.VehicleBrandItem::brand))
                .toList();
        return new ReportDtos.VehicleReport(vehicles.size(), items, items.stream().limit(5).toList());
    }

    @Transactional(readOnly = true)
    public ReportDtos.StaffPerformanceReport staffPerformance(ReportDtos.ReportRange range) {
        List<Booking> bookings = bookingsInRange(range);
        List<User> staff = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ROLE_STAFF)
                .toList();
        List<ReportDtos.StaffPerformanceItem> items = staff.stream().map(user -> {
            List<Booking> assigned = bookings.stream().filter(booking -> isAssignedTo(booking, user)).toList();
            long completed = assigned.stream().filter(this::isCompleted).count();
            long cancelled = assigned.stream().filter(booking -> booking.getStatus() == BookingStatus.CANCELLED).count();
            long active = assigned.size() - completed - cancelled;
            BigDecimal revenue = assigned.stream().filter(this::isRealizedRevenue)
                    .map(booking -> nullToZero(booking.getFinalAmount()).divide(
                            BigDecimal.valueOf(assignedStaffCount(booking)), 2, RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long averageMinutes = roundedAverage(assigned.stream()
                    .filter(this::hasServiceDuration)
                    .mapToLong(booking -> Duration.between(serviceStart(booking), booking.getCompletedAt()).toMinutes())
                    .boxed().toList());
            return new ReportDtos.StaffPerformanceItem(
                    String.valueOf(user.getId()), user.getFullName(), assigned.size(), completed, cancelled, active,
                    percentage(completed, assigned.size()), revenue, averageMinutes);
        }).sorted(Comparator.comparingLong(ReportDtos.StaffPerformanceItem::completedBookings).reversed()
                .thenComparing(ReportDtos.StaffPerformanceItem::staffName)).toList();
        return new ReportDtos.StaffPerformanceReport(items);
    }

    @Transactional(readOnly = true)
    public ReportDtos.ServiceTimeReport serviceTimes(ReportDtos.ReportRange range) {
        List<Booking> bookings = bookingsInRange(range);
        List<Booking> waiting = bookings.stream().filter(booking -> booking.getCheckInAt() != null).toList();
        List<Booking> serviced = bookings.stream().filter(this::hasServiceDuration).toList();
        long onTime = waiting.stream().filter(booking -> !booking.getCheckInAt().isAfter(booking.getScheduledAt().plusMinutes(15))).count();
        Map<YearMonth, List<Booking>> grouped = serviced.stream().collect(Collectors.groupingBy(
                booking -> YearMonth.from(booking.getScheduledAt()), LinkedHashMap::new, Collectors.toList()));
        List<ReportDtos.ServiceTimePeriodItem> periods = grouped.entrySet().stream().map(entry -> {
            List<Booking> values = entry.getValue();
            List<Long> waitMinutes = values.stream().filter(booking -> booking.getCheckInAt() != null)
                    .map(booking -> nonNegativeMinutes(booking.getScheduledAt(), booking.getCheckInAt())).toList();
            List<Long> serviceMinutes = values.stream()
                    .map(booking -> nonNegativeMinutes(serviceStart(booking), booking.getCompletedAt())).toList();
            return new ReportDtos.ServiceTimePeriodItem(entry.getKey().getYear(), entry.getKey().getMonthValue(),
                    entry.getKey().toString(), values.size(), roundedAverage(waitMinutes), roundedAverage(serviceMinutes));
        }).toList();
        return new ReportDtos.ServiceTimeReport(
                waiting.size(), serviced.size(),
                roundedAverage(waiting.stream().map(booking -> nonNegativeMinutes(booking.getScheduledAt(), booking.getCheckInAt())).toList()),
                roundedAverage(serviced.stream().map(booking -> nonNegativeMinutes(serviceStart(booking), booking.getCompletedAt())).toList()),
                onTime, percentage(onTime, waiting.size()), periods);
    }

    @Transactional(readOnly = true)
    public ReportDtos.PromotionEffectivenessReport promotionEffectiveness(ReportDtos.ReportRange range) {
        List<Booking> paid = paidBookingsInReportRange(range);
        List<Booking> promoted = paid.stream().filter(booking -> booking.getPromotion() != null).toList();
        List<Booking> regular = paid.stream().filter(booking -> booking.getPromotion() == null).toList();
        Map<Long, List<Booking>> byPromotion = promoted.stream().collect(Collectors.groupingBy(
                booking -> booking.getPromotion().getId(), LinkedHashMap::new, Collectors.toList()));
        List<ReportDtos.PromotionEffectivenessItem> items = byPromotion.values().stream().map(values -> {
            Promotion promotion = values.get(0).getPromotion();
            BigDecimal revenue = sumAmount(values, Booking::getFinalAmount);
            return new ReportDtos.PromotionEffectivenessItem(String.valueOf(promotion.getId()), promotion.getCode(),
                    promotion.getTitle(), values.size(), values.stream().map(b -> b.getCustomer().getId()).distinct().count(),
                    sumAmount(values, Booking::getDiscountAmount), revenue, averageAmount(revenue, values.size()));
        }).sorted(Comparator.comparing(ReportDtos.PromotionEffectivenessItem::revenue).reversed()).toList();
        BigDecimal promotionRevenue = sumAmount(promoted, Booking::getFinalAmount);
        BigDecimal regularRevenue = sumAmount(regular, Booking::getFinalAmount);
        return new ReportDtos.PromotionEffectivenessReport(promoted.size(), regular.size(),
                sumAmount(promoted, Booking::getDiscountAmount), promotionRevenue, regularRevenue,
                averageAmount(promotionRevenue, promoted.size()), averageAmount(regularRevenue, regular.size()), items);
    }

    @Transactional(readOnly = true)
    public ReportDtos.CustomerRetentionReport customerRetention(ReportDtos.ReportRange range) {
        LocalDateTime asOf = range.endExclusive() == null ? LocalDateTime.now() : range.endExclusive();
        Map<Long, List<Booking>> byCustomer = bookingRepository.findAll().stream().filter(this::isCompleted)
                .filter(booking -> booking.getCompletedAt() != null && booking.getCompletedAt().isBefore(asOf))
                .collect(Collectors.groupingBy(booking -> booking.getCustomer().getId()));
        long oneTime = byCustomer.values().stream().filter(values -> values.size() == 1).count();
        long returning = byCustomer.values().stream().filter(values -> values.size() >= 2).count();
        long loyal = byCustomer.values().stream().filter(values -> values.size() >= 4).count();
        long atRisk = byCustomer.values().stream().filter(values -> daysSinceLast(values, asOf) >= 30 && daysSinceLast(values, asOf) < 90).count();
        long inactive = byCustomer.values().stream().filter(values -> daysSinceLast(values, asOf) >= 90).count();
        List<ReportDtos.CustomerSegmentItem> segments = List.of(
                new ReportDtos.CustomerSegmentItem("one_time", oneTime),
                new ReportDtos.CustomerSegmentItem("returning", returning),
                new ReportDtos.CustomerSegmentItem("loyal", loyal),
                new ReportDtos.CustomerSegmentItem("at_risk", atRisk),
                new ReportDtos.CustomerSegmentItem("inactive", inactive));
        List<ReportDtos.TopCustomerItem> top = byCustomer.values().stream().map(values -> {
            User customer = values.get(0).getCustomer();
            LocalDateTime last = values.stream().map(Booking::getCompletedAt).max(LocalDateTime::compareTo).orElse(null);
            return new ReportDtos.TopCustomerItem(String.valueOf(customer.getId()), customer.getFullName(), values.size(),
                    sumAmount(values.stream().filter(this::isRealizedRevenue).toList(), Booking::getFinalAmount), last);
        }).sorted(Comparator.comparing(ReportDtos.TopCustomerItem::totalSpent).reversed()).limit(10).toList();
        return new ReportDtos.CustomerRetentionReport(byCustomer.size(), oneTime, returning, loyal, atRisk, inactive,
                percentage(returning, byCustomer.size()), segments, top);
    }

    @Transactional(readOnly = true)
    public ReportDtos.OperationalAlertReport operationalAlerts(LocalDateTime now) {
        List<ReportDtos.OperationalAlertItem> alerts = new ArrayList<>();
        for (Booking booking : bookingRepository.findAll()) {
            if (isActiveStatus(booking.getStatus()) && booking.getAssignedStaff() == null
                    && booking.getScheduledAt().isBefore(now.plusHours(24)) && !booking.getScheduledAt().isBefore(now)) {
                alerts.add(alert("UNASSIGNED", "HIGH", booking, "Lịch hẹn trong 24 giờ tới chưa được phân công nhân viên.", booking.getScheduledAt()));
            }
            if (Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED).contains(booking.getStatus())
                    && booking.getCheckInAt() == null && booking.getScheduledAt().plusMinutes(15).isBefore(now)) {
                alerts.add(alert("OVERDUE_CHECK_IN", "HIGH", booking, "Lịch hẹn đã quá giờ nhưng khách hàng chưa check-in.", booking.getScheduledAt()));
            }
            if (booking.getStatus() == BookingStatus.IN_PROGRESS && serviceStart(booking) != null
                    && serviceStart(booking).plusHours(3).isBefore(now)) {
                alerts.add(alert("LONG_RUNNING", "MEDIUM", booking, "Lịch hẹn đang được phục vụ quá 3 giờ.", serviceStart(booking)));
            }
            if (booking.getStatus() == BookingStatus.COMPLETED && booking.getPaymentStatus() != BookingPaymentStatus.PAID) {
                alerts.add(alert("COMPLETED_UNPAID", "HIGH", booking, "Lịch hẹn đã hoàn thành nhưng chưa được thanh toán.", booking.getCompletedAt()));
            }
            if (booking.getStatus() == BookingStatus.CANCELLED && booking.isRefundRequired()) {
                alerts.add(alert("REFUND_REQUIRED", "HIGH", booking, "Lịch hẹn đã bị hủy và khoản thanh toán cần được xử lý hoàn tiền.", booking.getScheduledAt()));
            }
        }
        Map<String, Long> summary = alerts.stream().collect(Collectors.groupingBy(
                ReportDtos.OperationalAlertItem::type, LinkedHashMap::new, Collectors.counting()));
        return new ReportDtos.OperationalAlertReport(alerts.size(), summary, alerts.stream()
                .sorted(Comparator.comparing(ReportDtos.OperationalAlertItem::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))).toList());
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

    private List<Booking> bookingsInRange(ReportDtos.ReportRange range) {
        return bookingRepository.findAll().stream()
                .filter(booking -> dateInRange(booking.getScheduledAt(), range))
                .sorted(Comparator.comparing(Booking::getScheduledAt))
                .toList();
    }

    private List<Booking> paidBookingsInReportRange(ReportDtos.ReportRange range) {
        return bookingRepository.findAll().stream()
                .filter(this::isRealizedRevenue)
                .filter(booking -> dateInRange(reportDate(booking), range))
                .sorted(Comparator.comparing(this::reportDate))
                .toList();
    }

    private List<LoyaltyTransaction> transactionsInRange(ReportDtos.ReportRange range) {
        return transactionRepository.findAll().stream()
                .filter(transaction -> dateInRange(transaction.getCreatedAt(), range))
                .toList();
    }

    private int sumPoints(List<LoyaltyTransaction> transactions, LoyaltyTransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .filter(transaction -> type != LoyaltyTransactionType.EARN
                        || transaction.getStatus() == LoyaltyTransactionStatus.POSTED)
                .mapToInt(transaction -> Math.abs(transaction.getPoints()))
                .sum();
    }

    private boolean isCompleted(Booking booking) {
        return booking.getStatus() == BookingStatus.COMPLETED;
    }

    private boolean isRealizedRevenue(Booking booking) {
        return booking.getPaymentStatus() == BookingPaymentStatus.PAID
                && booking.getStatus() != BookingStatus.CANCELLED;
    }

    private LocalDateTime reportDate(Booking booking) {
        if (booking.getPaidAt() != null) {
            return booking.getPaidAt();
        }
        if (booking.getCompletedAt() != null) {
            return booking.getCompletedAt();
        }
        return booking.getScheduledAt();
    }

    private boolean createdInRange(LocalDateTime value, ReportDtos.ReportRange range) {
        return value != null && dateInRange(value, range);
    }

    private boolean dateInRange(LocalDateTime value, ReportDtos.ReportRange range) {
        return value != null
                && (range.start() == null || !value.isBefore(range.start()))
                && (range.endExclusive() == null || value.isBefore(range.endExclusive()));
    }

    private String groupKey(LocalDateTime value, String period) {
        return switch (normalizePeriod(period)) {
            case "daily" -> value.toLocalDate().toString();
            case "weekly" -> value.getYear() + "-W" + String.format("%02d", value.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case "yearly" -> String.valueOf(value.getYear());
            default -> YearMonth.from(value).toString();
        };
    }

    private String normalizePeriod(String period) {
        if (period == null) {
            return "monthly";
        }
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "daily", "weekly", "yearly" -> period.toLowerCase(Locale.ROOT);
            default -> "monthly";
        };
    }

    private long percentage(long value, long total) {
        if (total == 0) {
            return 0;
        }
        return BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isAssignedTo(Booking booking, User staff) {
        return sameUser(booking.getAssignedStaff(), staff) || sameUser(booking.getSecondaryAssignedStaff(), staff);
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && Objects.equals(first.getId(), second.getId());
    }

    private int assignedStaffCount(Booking booking) {
        return booking.getSecondaryAssignedStaff() == null ? 1 : 2;
    }

    private boolean hasServiceDuration(Booking booking) {
        return booking.getCompletedAt() != null && serviceStart(booking) != null
                && !booking.getCompletedAt().isBefore(serviceStart(booking));
    }

    private LocalDateTime serviceStart(Booking booking) {
        return booking.getServiceStartedAt() != null ? booking.getServiceStartedAt() : booking.getCheckInAt();
    }

    private long nonNegativeMinutes(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : Math.max(0, Duration.between(start, end).toMinutes());
    }

    private long roundedAverage(List<Long> values) {
        return values.isEmpty() ? 0 : Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private BigDecimal sumAmount(List<Booking> bookings, java.util.function.Function<Booking, BigDecimal> getter) {
        return bookings.stream().map(getter).map(this::nullToZero).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal averageAmount(BigDecimal total, long count) {
        return count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private long daysSinceLast(List<Booking> bookings, LocalDateTime asOf) {
        LocalDateTime last = bookings.stream().map(Booking::getCompletedAt).max(LocalDateTime::compareTo).orElse(asOf);
        return Math.max(0, Duration.between(last, asOf).toDays());
    }

    private boolean isActiveStatus(BookingStatus status) {
        return Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_QUEUE, BookingStatus.IN_PROGRESS).contains(status);
    }

    private ReportDtos.OperationalAlertItem alert(
            String type, String severity, Booking booking, String message, LocalDateTime occurredAt) {
        String vehicleName = booking.getVehicle() == null
                ? "Chưa có thông tin xe"
                : (safeLabel(booking.getVehicle().getBrand(), "") + " "
                                + safeLabel(booking.getVehicle().getModel(), ""))
                        .trim();
        return new ReportDtos.OperationalAlertItem(
                type,
                severity,
                String.valueOf(booking.getId()),
                message,
                occurredAt,
                booking.getCustomer() == null ? "Chưa có thông tin khách hàng" : booking.getCustomer().getFullName(),
                vehicleName.isBlank() ? "Chưa có thông tin xe" : vehicleName,
                booking.getVehicle() == null ? "" : booking.getVehicle().getLicensePlate(),
                booking.getScheduledAt());
    }

    private String safeLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class ServiceStats {
        private final String serviceId;
        private final String serviceName;
        private long usageCount;
        private BigDecimal revenue;

        private ServiceStats(String serviceId, String serviceName, long usageCount, BigDecimal revenue) {
            this.serviceId = serviceId;
            this.serviceName = serviceName;
            this.usageCount = usageCount;
            this.revenue = revenue;
        }

        private ReportDtos.ServiceReportItem toReportItem() {
            return new ReportDtos.ServiceReportItem(serviceId, serviceName, usageCount, revenue);
        }
    }
}
