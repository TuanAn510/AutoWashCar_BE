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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        List<Booking> paidBookings = bookings.stream().filter(this::isPaid).toList();
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
        long pending = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.PENDING).count();

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
        List<LoyaltyAccount> accounts = loyaltyAccountRepository.findAll();
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
                .filter(this::isPaid)
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

    private boolean isPaid(Booking booking) {
        return booking.getPaymentStatus() == BookingPaymentStatus.PAID;
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
