package com.shinecraft.server.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.shinecraft.server.loyalty.MembershipTier;
import com.shinecraft.server.loyalty.RewardRepository;
import com.shinecraft.server.promotion.DiscountType;
import com.shinecraft.server.promotion.Promotion;
import com.shinecraft.server.promotion.PromotionRepository;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceTest {
    private UserRepository userRepository;
    private BookingRepository bookingRepository;
    private VehicleRepository vehicleRepository;
    private CarWashServiceRepository serviceRepository;
    private PromotionRepository promotionRepository;
    private RewardRepository rewardRepository;
    private LoyaltyAccountRepository loyaltyAccountRepository;
    private LoyaltyTransactionRepository transactionRepository;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        bookingRepository = mock(BookingRepository.class);
        vehicleRepository = mock(VehicleRepository.class);
        serviceRepository = mock(CarWashServiceRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        rewardRepository = mock(RewardRepository.class);
        loyaltyAccountRepository = mock(LoyaltyAccountRepository.class);
        transactionRepository = mock(LoyaltyTransactionRepository.class);
        reportService = new ReportService(
                userRepository,
                bookingRepository,
                vehicleRepository,
                serviceRepository,
                promotionRepository,
                rewardRepository,
                loyaltyAccountRepository,
                transactionRepository);
    }

    @Test
    void dashboardReturnsExactAggregateValues() {
        Booking completed = booking(1L, BookingStatus.COMPLETED, "2026-02-01T09:00:00", "2026-02-01T10:00:00", "120000");
        completed.addService(bookingService(service(11L, "Basic"), "70000"));
        completed.addService(bookingService(service(12L, "Wax"), "50000"));
        Booking pending = booking(2L, BookingStatus.PENDING, "2026-02-02T09:00:00", null, "50000");
        Booking secondCompleted = booking(3L, BookingStatus.COMPLETED, "2026-02-03T09:00:00", null, "80000");
        Booking unpaidCompleted = booking(4L, BookingStatus.COMPLETED, "2026-02-04T09:00:00", "2026-02-04T10:00:00", "999999");
        markPaid(completed, "2026-02-01T10:05:00");
        markPaid(secondCompleted, "2026-02-03T10:05:00");

        when(userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_CUSTOMER)).thenReturn(List.of(user(1L), user(2L)));
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle("Toyota", true), vehicle("Honda", true), vehicle("Ford", false)));
        when(bookingRepository.findAll()).thenReturn(List.of(completed, pending, secondCompleted, unpaidCompleted));
        when(promotionRepository.findByIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(any(), any()))
                .thenReturn(List.of(new Promotion()));
        when(loyaltyAccountRepository.count()).thenReturn(2L);
        when(rewardRepository.findByIsActiveTrueOrderByRequiredPointsAsc()).thenReturn(List.of());
        when(transactionRepository.findAll()).thenReturn(List.of(
                transaction(LoyaltyTransactionType.EARN, LoyaltyTransactionStatus.POSTED, 10, "2026-02-01T10:00:00"),
                transaction(LoyaltyTransactionType.EARN, LoyaltyTransactionStatus.PENDING, 20, "2026-02-01T10:15:00"),
                transaction(LoyaltyTransactionType.EARN, LoyaltyTransactionStatus.REVERSED, 30, "2026-02-01T10:30:00"),
                transaction(LoyaltyTransactionType.REDEEM, -4, "2026-02-01T11:00:00")));

        ReportDtos.DashboardResponse dashboard = reportService.dashboard();

        assertThat(dashboard.customers()).isEqualTo(2);
        assertThat(dashboard.vehicles()).isEqualTo(2);
        assertThat(dashboard.bookings()).isEqualTo(4);
        assertThat(dashboard.completedBookings()).isEqualTo(3);
        assertThat(dashboard.completedServices()).isEqualTo(2);
        assertThat(dashboard.activePromotions()).isEqualTo(1);
        assertThat(dashboard.loyaltyMembers()).isEqualTo(2);
        assertThat(dashboard.revenue()).isEqualByComparingTo("200000");
        assertThat(dashboard.issuedPoints()).isEqualTo(10);
        assertThat(dashboard.redeemedPoints()).isEqualTo(4);
    }

    @Test
    void revenueAndServiceReportsUsePaidAtForPaidBookings() {
        CarWashService basic = service(11L, "Basic");
        CarWashService premium = service(12L, "Premium");
        Booking paidInRange = booking(1L, BookingStatus.COMPLETED, "2026-01-31T23:30:00", "2026-01-31T23:55:00", "100000");
        paidInRange.addService(bookingService(basic, "70000"));
        markPaid(paidInRange, "2026-02-01T00:15:00");
        Booking paidAfterRange = booking(2L, BookingStatus.COMPLETED, "2026-02-10T09:00:00", "2026-02-10T10:00:00", "200000");
        paidAfterRange.addService(bookingService(premium, "200000"));
        markPaid(paidAfterRange, "2026-03-02T10:00:00");
        Booking unpaidInRange = booking(3L, BookingStatus.COMPLETED, "2026-02-05T09:00:00", "2026-02-05T10:00:00", "300000");
        unpaidInRange.addService(bookingService(premium, "300000"));
        ReportDtos.ReportRange february = new ReportDtos.ReportRange(
                LocalDateTime.parse("2026-02-01T00:00:00"),
                LocalDateTime.parse("2026-03-01T00:00:00"));

        when(bookingRepository.findAll()).thenReturn(List.of(paidInRange, paidAfterRange, unpaidInRange));
        when(serviceRepository.findAll()).thenReturn(List.of(basic, premium));

        ReportDtos.RevenueReport revenue = reportService.revenue(february, "daily");
        ReportDtos.ServiceReport services = reportService.services(february, 5);

        assertThat(revenue.data()).singleElement().satisfies(item -> {
            assertThat(item.period()).isEqualTo("2026-02-01");
            assertThat(item.revenue()).isEqualByComparingTo("100000");
            assertThat(item.completedServicesCount()).isEqualTo(1);
        });
        assertThat(services.mostBookedServices().get(0)).satisfies(item -> {
            assertThat(item.serviceId()).isEqualTo("11");
            assertThat(item.serviceName()).isEqualTo("Basic");
            assertThat(item.usageCount()).isEqualTo(1);
            assertThat(item.revenue()).isEqualByComparingTo("70000");
        });
        assertThat(services.mostBookedServices())
                .filteredOn(item -> item.serviceId().equals("12"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.usageCount()).isZero();
                    assertThat(item.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
                });
    }

    @Test
    void reportsReturnExactValuesForAppointmentsCustomersLoyaltyPromotionsAndVehicles() {
        User activeCustomer = user(1L);
        activeCustomer.setCreatedAt(LocalDateTime.parse("2026-02-03T09:00:00"));
        User inactiveCustomer = user(2L);
        inactiveCustomer.setActive(false);
        inactiveCustomer.setCreatedAt(LocalDateTime.parse("2026-01-10T09:00:00"));
        User staff = user(3L);
        staff.setRole(UserRole.ROLE_STAFF);
        staff.setCreatedAt(LocalDateTime.parse("2026-02-03T09:00:00"));
        Booking completed = booking(1L, BookingStatus.COMPLETED, "2026-02-04T09:00:00", null, "100000");
        completed.setCustomer(activeCustomer);
        Booking cancelled = booking(2L, BookingStatus.CANCELLED, "2026-02-05T09:00:00", null, "100000");
        cancelled.setCustomer(activeCustomer);
        Booking pending = booking(3L, BookingStatus.PENDING, "2026-02-06T09:00:00", null, "100000");
        pending.setCustomer(inactiveCustomer);
        Booking returningVisit = booking(4L, BookingStatus.COMPLETED, "2026-02-07T09:00:00", null, "100000");
        returningVisit.setCustomer(activeCustomer);
        ReportDtos.ReportRange february = new ReportDtos.ReportRange(
                LocalDateTime.parse("2026-02-01T00:00:00"),
                LocalDateTime.parse("2026-03-01T00:00:00"));

        MembershipTier silver = new MembershipTier();
        silver.setName("Silver");
        LoyaltyAccount account = new LoyaltyAccount();
        account.setMembershipTier(silver);
        Promotion activePromotion = promotion("PERCENTAGE", 2, true, "2026-02-01T00:00:00", "2026-12-31T23:59:59");
        Promotion expiredPromotion = promotion("FIXED_AMOUNT", 1, true, "2026-01-01T00:00:00", "2026-02-02T00:00:00");

        when(bookingRepository.findAll()).thenReturn(List.of(completed, cancelled, pending, returningVisit));
        when(userRepository.findAll()).thenReturn(List.of(activeCustomer, inactiveCustomer, staff));
        when(userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_CUSTOMER)).thenReturn(List.of(activeCustomer));
        when(transactionRepository.findAll()).thenReturn(List.of(
                transaction(LoyaltyTransactionType.EARN, LoyaltyTransactionStatus.POSTED, 12, "2026-02-04T10:00:00"),
                transaction(LoyaltyTransactionType.EARN, LoyaltyTransactionStatus.PENDING, 20, "2026-02-04T10:15:00"),
                transaction(LoyaltyTransactionType.EARN, LoyaltyTransactionStatus.REVERSED, 30, "2026-02-04T10:30:00"),
                transaction(LoyaltyTransactionType.REDEEM, -5, "2026-02-05T10:00:00"),
                transaction(LoyaltyTransactionType.EXPIRE, -2, "2026-02-06T10:00:00"),
                transaction(LoyaltyTransactionType.EARN, 99, "2026-03-01T00:00:00")));
        when(loyaltyAccountRepository.findAll()).thenReturn(List.of(account));
        when(promotionRepository.findAll()).thenReturn(List.of(activePromotion, expiredPromotion));
        when(vehicleRepository.findAll()).thenReturn(List.of(
                vehicle("Toyota", true, "2026-02-01T09:00:00"),
                vehicle("Toyota", true, "2026-02-02T09:00:00"),
                vehicle("Honda", true, "2026-02-03T09:00:00"),
                vehicle("Ford", false, "2026-02-04T09:00:00"),
                vehicle("Mazda", true, "2026-03-01T00:00:00")));

        ReportDtos.AppointmentReport appointments = reportService.appointments(february);
        ReportDtos.CustomerReport customers = reportService.customers(february);
        ReportDtos.LoyaltyReport loyalty = reportService.loyalty(february);
        ReportDtos.PromotionReport promotions = reportService.promotions(february);
        ReportDtos.VehicleReport vehicles = reportService.vehicles(february);

        assertThat(appointments.totalAppointments()).isEqualTo(4);
        assertThat(appointments.completedAppointments()).isEqualTo(2);
        assertThat(appointments.cancelledAppointments()).isEqualTo(1);
        assertThat(appointments.pendingAppointments()).isEqualTo(1);
        assertThat(appointments.completionRate()).isEqualTo(50);
        assertThat(appointments.cancellationRate()).isEqualTo(25);
        assertThat(customers.totalCustomers()).isEqualTo(2);
        assertThat(customers.newCustomersInRange()).isEqualTo(1);
        assertThat(customers.activeCustomers()).isEqualTo(1);
        assertThat(customers.returningCustomers()).isEqualTo(1);
        assertThat(loyalty.totalLoyaltyMembers()).isEqualTo(1);
        assertThat(loyalty.pointsIssued()).isEqualTo(12);
        assertThat(loyalty.pointsRedeemed()).isEqualTo(5);
        assertThat(loyalty.pointsExpired()).isEqualTo(2);
        assertThat(loyalty.membershipTierDistribution()).singleElement().satisfies(item -> {
            assertThat(item.tier()).isEqualTo("Silver");
            assertThat(item.total()).isEqualTo(1);
        });
        assertThat(promotions.totalPromotions()).isEqualTo(2);
        assertThat(promotions.promotionUsageCount()).isEqualTo(3);
        assertThat(promotions.distributionByType()).hasSize(2);
        assertThat(vehicles.totalVehicles()).isEqualTo(3);
        assertThat(vehicles.mostCommonVehicleBrands().get(0)).satisfies(item -> {
            assertThat(item.brand()).isEqualTo("Toyota");
            assertThat(item.total()).isEqualTo(2);
        });
    }

    @Test
    void advancedReportsCalculateStaffTimePromotionRetentionAndAlerts() {
        User customer = user(1L);
        User staff = user(10L);
        staff.setRole(UserRole.ROLE_STAFF);
        staff.setFullName("Staff A");
        Promotion promotion = promotion("FIXED_AMOUNT", 1, true, "2026-01-01T00:00:00", "2026-12-31T23:59:59");
        promotion.setId(5L);
        Booking completed = booking(1L, BookingStatus.COMPLETED, "2026-02-01T09:00:00", "2026-02-01T10:10:00", "90000");
        completed.setCustomer(customer);
        completed.setAssignedStaff(staff);
        completed.setCheckInAt(LocalDateTime.parse("2026-02-01T09:10:00"));
        completed.setServiceStartedAt(LocalDateTime.parse("2026-02-01T09:20:00"));
        completed.setPromotion(promotion);
        completed.setDiscountAmount(new BigDecimal("10000"));
        markPaid(completed, "2026-02-01T10:10:00");
        Booking completedAgain = booking(2L, BookingStatus.COMPLETED, "2026-02-10T09:00:00", "2026-02-10T10:00:00", "100000");
        completedAgain.setCustomer(customer);
        completedAgain.setAssignedStaff(staff);
        completedAgain.setCheckInAt(LocalDateTime.parse("2026-02-10T09:20:00"));
        markPaid(completedAgain, "2026-02-10T10:00:00");
        Booking unpaid = booking(3L, BookingStatus.COMPLETED, "2026-02-11T09:00:00", "2026-02-11T10:00:00", "50000");

        when(bookingRepository.findAll()).thenReturn(List.of(completed, completedAgain, unpaid));
        when(userRepository.findAll()).thenReturn(List.of(customer, staff));
        ReportDtos.ReportRange february = new ReportDtos.ReportRange(
                LocalDateTime.parse("2026-02-01T00:00:00"), LocalDateTime.parse("2026-03-01T00:00:00"));

        assertThat(reportService.staffPerformance(february).staff()).singleElement().satisfies(item -> {
            assertThat(item.assignedBookings()).isEqualTo(2);
            assertThat(item.completedBookings()).isEqualTo(2);
            assertThat(item.attributedRevenue()).isEqualByComparingTo("190000");
        });
        assertThat(reportService.serviceTimes(february)).satisfies(item -> {
            assertThat(item.averageWaitingMinutes()).isEqualTo(15);
            assertThat(item.averageServiceMinutes()).isEqualTo(45);
            assertThat(item.onTimeRate()).isEqualTo(50);
        });
        assertThat(reportService.promotionEffectiveness(february)).satisfies(item -> {
            assertThat(item.bookingsWithPromotion()).isEqualTo(1);
            assertThat(item.totalDiscount()).isEqualByComparingTo("10000");
        });
        assertThat(reportService.customerRetention(february)).satisfies(item -> {
            assertThat(item.returningCustomers()).isEqualTo(1);
            assertThat(item.retentionRate()).isEqualTo(50);
        });
        ReportDtos.OperationalAlertReport alerts =
                reportService.operationalAlerts(LocalDateTime.parse("2026-02-12T09:00:00"));
        assertThat(alerts.summary()).containsEntry("COMPLETED_UNPAID", 1L);
        assertThat(alerts.alerts()).allSatisfy(item ->
                assertThat(item.message()).doesNotContain("Booking da", "Booking trong"));
        assertThat(alerts.alerts()).filteredOn(item -> item.bookingId().equals("3")).singleElement().satisfies(item -> {
            assertThat(item.customerName()).isEqualTo("Customer 103");
            assertThat(item.vehicleName()).isEqualTo("Toyota Model");
            assertThat(item.licensePlate()).isEqualTo("TOYOTA123");
            assertThat(item.scheduledAt()).isEqualTo(LocalDateTime.parse("2026-02-11T09:00:00"));
            assertThat(item.message()).isEqualTo("Lịch hẹn đã hoàn thành nhưng chưa được thanh toán.");
        });
    }

    @Test
    void operationalAlertsKeepAllTypesAndStandardizedBookingMessages() {
        LocalDateTime now = LocalDateTime.parse("2026-08-25T12:00:00");
        User staff = user(500L);

        Booking unassigned = booking(11L, BookingStatus.CONFIRMED, "2026-08-25T13:40:00", null, "100000");
        Booking overdue = booking(12L, BookingStatus.CONFIRMED, "2026-08-25T10:00:00", null, "100000");
        overdue.setAssignedStaff(staff);
        Booking longRunning = booking(13L, BookingStatus.IN_PROGRESS, "2026-08-25T08:00:00", null, "100000");
        longRunning.setAssignedStaff(staff);
        longRunning.setServiceStartedAt(LocalDateTime.parse("2026-08-25T08:30:00"));
        Booking unpaid = booking(14L, BookingStatus.COMPLETED, "2026-08-24T09:00:00", "2026-08-24T10:00:00", "100000");
        Booking refund = booking(15L, BookingStatus.CANCELLED, "2026-08-20T09:30:00", null, "100000");
        refund.setRefundRequired(true);

        when(bookingRepository.findAll()).thenReturn(List.of(unassigned, overdue, longRunning, unpaid, refund));

        ReportDtos.OperationalAlertReport result = reportService.operationalAlerts(now);

        assertThat(result.total()).isEqualTo(5);
        assertThat(result.summary()).containsOnly(
                org.assertj.core.api.Assertions.entry("UNASSIGNED", 1L),
                org.assertj.core.api.Assertions.entry("OVERDUE_CHECK_IN", 1L),
                org.assertj.core.api.Assertions.entry("LONG_RUNNING", 1L),
                org.assertj.core.api.Assertions.entry("COMPLETED_UNPAID", 1L),
                org.assertj.core.api.Assertions.entry("REFUND_REQUIRED", 1L));
        assertThat(result.alerts()).extracting(ReportDtos.OperationalAlertItem::message).containsExactlyInAnyOrder(
                "Lịch hẹn trong 24 giờ tới chưa được phân công nhân viên.",
                "Lịch hẹn đã quá giờ nhưng khách hàng chưa check-in.",
                "Lịch hẹn đang được phục vụ quá 3 giờ.",
                "Lịch hẹn đã hoàn thành nhưng chưa được thanh toán.",
                "Lịch hẹn đã bị hủy và khoản thanh toán cần được xử lý hoàn tiền.");
        assertThat(result.alerts()).allSatisfy(item -> {
            assertThat(item.bookingId()).isNotBlank();
            assertThat(item.customerName()).isNotBlank();
            assertThat(item.vehicleName()).isNotBlank();
            assertThat(item.licensePlate()).isNotBlank();
            assertThat(item.scheduledAt()).isNotNull();
        });
    }

    private Booking booking(Long id, BookingStatus status, String scheduledAt, String completedAt, String finalAmount) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setCustomer(user(100L + id));
        booking.setVehicle(vehicle("Toyota", true));
        booking.setScheduledAt(LocalDateTime.parse(scheduledAt));
        booking.setCompletedAt(completedAt == null ? null : LocalDateTime.parse(completedAt));
        booking.setStatus(status);
        booking.setSubtotalAmount(new BigDecimal(finalAmount));
        booking.setFinalAmount(new BigDecimal(finalAmount));
        return booking;
    }

    private void markPaid(Booking booking, String paidAt) {
        booking.setPaymentStatus(BookingPaymentStatus.PAID);
        booking.setPaidAt(LocalDateTime.parse(paidAt));
    }

    private BookingService bookingService(CarWashService service, String price) {
        BookingService item = new BookingService();
        item.setService(service);
        item.setServiceName(service.getName());
        item.setPrice(new BigDecimal(price));
        item.setDurationMinutes(30);
        return item;
    }

    private CarWashService service(Long id, String name) {
        CarWashService service = new CarWashService();
        service.setId(id);
        service.setName(name);
        service.setPrice(BigDecimal.TEN);
        service.setDurationMinutes(30);
        service.setActive(true);
        return service;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(UserRole.ROLE_CUSTOMER);
        user.setFullName("Customer " + id);
        user.setPhone("09000000" + id);
        user.setPasswordHash("hash");
        user.setActive(true);
        return user;
    }

    private Vehicle vehicle(String brand, boolean active) {
        return vehicle(brand, active, "2026-02-01T09:00:00");
    }

    private Vehicle vehicle(String brand, boolean active, String createdAt) {
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand(brand);
        vehicle.setModel("Model");
        vehicle.setLicensePlate(brand.toUpperCase() + "123");
        vehicle.setActive(active);
        vehicle.setCreatedAt(LocalDateTime.parse(createdAt));
        return vehicle;
    }

    private LoyaltyTransaction transaction(LoyaltyTransactionType type, int points, String createdAt) {
        return transaction(type, LoyaltyTransactionStatus.POSTED, points, createdAt);
    }

    private LoyaltyTransaction transaction(
            LoyaltyTransactionType type, LoyaltyTransactionStatus status, int points, String createdAt) {
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setPoints(points);
        transaction.setCreatedAt(LocalDateTime.parse(createdAt));
        return transaction;
    }

    private Promotion promotion(String type, int usedCount, boolean active, String startAt, String endAt) {
        Promotion promotion = new Promotion();
        promotion.setCode(type + usedCount);
        promotion.setTitle(type);
        promotion.setDiscountType(DiscountType.valueOf(type));
        promotion.setDiscountValue(BigDecimal.TEN);
        promotion.setUsedCount(usedCount);
        promotion.setActive(active);
        promotion.setStartAt(LocalDateTime.parse(startAt));
        promotion.setEndAt(LocalDateTime.parse(endAt));
        promotion.setCreatedAt(LocalDateTime.parse("2026-02-01T09:00:00"));
        return promotion;
    }
}
