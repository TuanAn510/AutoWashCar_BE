package com.shinecraft.server.config;

import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.booking.BookingPaymentMethod;
import com.shinecraft.server.booking.BookingPaymentStatus;
import com.shinecraft.server.booking.BookingRepository;
import com.shinecraft.server.booking.BookingService;
import com.shinecraft.server.booking.BookingStatus;
import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.catalog.ServiceCategory;
import com.shinecraft.server.catalog.ServiceCategoryRepository;
import com.shinecraft.server.loyalty.LoyaltyTransaction;
import com.shinecraft.server.loyalty.LoyaltyTransactionRepository;
import com.shinecraft.server.loyalty.LoyaltyTransactionType;
import com.shinecraft.server.loyalty.MembershipTier;
import com.shinecraft.server.loyalty.MembershipTierRepository;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyAccountRepository;
import com.shinecraft.server.loyalty.Reward;
import com.shinecraft.server.loyalty.RewardRepository;
import com.shinecraft.server.loyalty.RewardType;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final MembershipTierRepository tierRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final CarWashServiceRepository serviceRepository;
    private final RewardRepository rewardRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPhone;
    private final String adminPassword;
    private final String staffPhone;
    private final String staffPassword;
    private final String customerOnePhone;
    private final String customerTwoPhone;
    private final String customerThreePhone;
    private final String customerPassword;
    private final boolean staffDemoDataEnabled;

    public DataSeeder(
            UserRepository userRepository,
            MembershipTierRepository tierRepository,
            LoyaltyAccountRepository loyaltyAccountRepository,
            LoyaltyTransactionRepository loyaltyTransactionRepository,
            ServiceCategoryRepository categoryRepository,
            CarWashServiceRepository serviceRepository,
            RewardRepository rewardRepository,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.seed-phone}") String adminPhone,
            @Value("${app.admin.seed-password}") String adminPassword,
            @Value("${app.staff.seed-phone:0987654321}") String staffPhone,
            @Value("${app.staff.seed-password:Staff@123456}") String staffPassword,
            @Value("${app.customer-one.seed-phone:0911111111}") String customerOnePhone,
            @Value("${app.customer-two.seed-phone:0922222222}") String customerTwoPhone,
            @Value("${app.customer-three.seed-phone:0933333333}") String customerThreePhone,
            @Value("${app.customer.seed-password:Customer@123456}") String customerPassword,
            @Value("${app.staff.demo-data-enabled:true}") boolean staffDemoDataEnabled) {
        this.userRepository = userRepository;
        this.tierRepository = tierRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.loyaltyTransactionRepository = loyaltyTransactionRepository;
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.rewardRepository = rewardRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
        this.staffPhone = staffPhone;
        this.staffPassword = staffPassword;
        this.customerOnePhone = customerOnePhone;
        this.customerTwoPhone = customerTwoPhone;
        this.customerThreePhone = customerThreePhone;
        this.customerPassword = customerPassword;
        this.staffDemoDataEnabled = staffDemoDataEnabled;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedStaff();
        seedTiers();
        seedCustomers();
        seedCatalog();
        seedRewards();
        seedStaffDemoData();
    }

    private void seedAdmin() {
        if (userRepository.existsByPhone(adminPhone)) {
            return;
        }
        User admin = new User();
        admin.setFullName("System Admin");
        admin.setPhone(adminPhone);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ROLE_ADMIN);
        userRepository.save(admin);
    }

    private void seedStaff() {
        if (userRepository.existsByPhone(staffPhone)) {
            return;
        }
        User staff = new User();
        staff.setFullName("System Staff");
        staff.setPhone(staffPhone);
        staff.setPasswordHash(passwordEncoder.encode(staffPassword));
        staff.setRole(UserRole.ROLE_STAFF);
        userRepository.save(staff);
    }

    private void seedTiers() {
        if (!tierRepository.findByIsActiveTrueOrderByMinPointsAsc().isEmpty()) {
            return;
        }
        createTier("Member", 0, "0", 7, 0);
        createTier("Silver", 500, "5", 10, 1);
        createTier("Gold", 1500, "10", 12, 2);
        createTier("Platinum", 3000, "15", 14, 3);
    }

    private void createTier(String name, int minPoints, String discount, int windowDays, int priority) {
        MembershipTier tier = new MembershipTier();
        tier.setName(name);
        tier.setMinPoints(minPoints);
        tier.setDiscountPercent(new BigDecimal(discount));
        tier.setBookingWindowDays(windowDays);
        tier.setPriorityLevel(priority);
        tier.setDescription("Default " + name + " tier");
        tierRepository.save(tier);
    }

    private void seedCustomers() {
        createCustomer("Nguyen Van An", customerOnePhone, customerPassword);
        createCustomer("Tran Thi Binh", customerTwoPhone, customerPassword);
        createCustomer("Le Hoang Minh", customerThreePhone, customerPassword);
    }

    private void createCustomer(String fullName, String phone, String password) {
        if (userRepository.existsByPhone(phone)) {
            return;
        }

        User customer = new User();
        customer.setFullName(fullName);
        customer.setPhone(phone);
        customer.setPasswordHash(passwordEncoder.encode(password));
        customer.setRole(UserRole.ROLE_CUSTOMER);
        customer = userRepository.save(customer);

        LoyaltyAccount account = new LoyaltyAccount();
        account.setCustomer(customer);
        account.setMembershipTier(tierRepository
                .findFirstByIsActiveTrueAndMinPointsLessThanEqualOrderByMinPointsDesc(0)
                .orElse(null));
        loyaltyAccountRepository.save(account);
    }

    private void seedCatalog() {
        if (!serviceRepository.findByIsActiveTrueOrderByNameAsc().isEmpty()) {
            return;
        }
        ServiceCategory wash = new ServiceCategory();
        wash.setName("Car Wash");
        wash.setDescription("Standard car wash services");
        categoryRepository.save(wash);

        ServiceCategory detailing = new ServiceCategory();
        detailing.setName("Car Care");
        detailing.setDescription("Care and deep cleaning services");
        categoryRepository.save(detailing);

        createService(wash, "Basic Wash", "Exterior cleaning", "150000", 30);
        createService(wash, "Premium Wash", "Exterior wash and interior vacuum", "350000", 45);
        createService(detailing, "Interior Cleaning", "Deep interior cleaning", "850000", 90);
    }

    private void createService(ServiceCategory category, String name, String description, String price, int duration) {
        CarWashService service = new CarWashService();
        service.setCategory(category);
        service.setName(name);
        service.setDescription(description);
        service.setPrice(new BigDecimal(price));
        service.setDurationMinutes(duration);
        serviceRepository.save(service);
    }

    private void seedRewards() {
        if (!rewardRepository.findByIsActiveTrueOrderByRequiredPointsAsc().isEmpty()) {
            return;
        }
        Reward discount = new Reward();
        discount.setName("50K Discount Voucher");
        discount.setDescription("Redeem points for a 50,000 VND discount code");
        discount.setRequiredPoints(300);
        discount.setRewardType(RewardType.DISCOUNT_CODE);
        discount.setDiscountAmount(new BigDecimal("50000"));
        rewardRepository.save(discount);

        Reward freeWash = new Reward();
        freeWash.setName("Free Car Wash");
        freeWash.setDescription("Redeem points for one free car wash");
        freeWash.setRequiredPoints(1000);
        freeWash.setRewardType(RewardType.FREE_WASH);
        rewardRepository.save(freeWash);
    }

    private void seedStaffDemoData() {
        if (!staffDemoDataEnabled) {
            return;
        }
        User staff = userRepository.findByPhone(staffPhone).orElse(null);
        User customerOne = userRepository.findByPhone(customerOnePhone).orElse(null);
        User customerTwo = userRepository.findByPhone(customerTwoPhone).orElse(null);
        List<CarWashService> services = serviceRepository.findByIsActiveTrueOrderByNameAsc();
        if (staff == null || customerOne == null || customerTwo == null || services.isEmpty()) {
            return;
        }

        Vehicle vehicleOne = vehicleRepository
                .findByLicensePlateAndIsActiveTrue("30A-11111")
                .orElseGet(() -> createVehicle(customerOne, "30A-11111", "Toyota", "Vios", "White", 2022));
        Vehicle vehicleTwo = vehicleRepository
                .findByLicensePlateAndIsActiveTrue("30B-22222")
                .orElseGet(() -> createVehicle(customerTwo, "30B-22222", "Mazda", "CX-5", "Red", 2021));

        CarWashService basic = services.get(0);
        CarWashService premium = services.size() > 1 ? services.get(1) : services.get(0);
        LocalDate today = LocalDate.now();

        createDemoBookingIfMissing(
                "Khách gửi xe, cần kiểm tra trước khi rửa.",
                customerOne,
                vehicleOne,
                staff,
                List.of(basic),
                today.atTime(9, 17),
                BookingStatus.CONFIRMED,
                BookingPaymentStatus.UNPAID,
                null);

        Booking completed = createDemoBookingIfMissing(
                "Đã hoàn thành vệ sinh nội thất và rửa ngoài.",
                customerOne,
                vehicleOne,
                staff,
                List.of(premium),
                today.minusDays(7).atTime(14, 41),
                BookingStatus.COMPLETED,
                BookingPaymentStatus.PAID,
                today.minusDays(7).atTime(16, 0));

        createDemoBookingIfMissing(
                "Khách đặt lịch chăm sóc xe ngày mai.",
                customerTwo,
                vehicleTwo,
                staff,
                List.of(basic, premium),
                today.plusDays(1).atTime(10, 23),
                BookingStatus.IN_QUEUE,
                BookingPaymentStatus.UNPAID,
                null);

        seedDemoLoyalty(customerOne, completed);
        seedDemoLoyalty(customerTwo, null);
    }

    private Vehicle createVehicle(
            User customer, String licensePlate, String brand, String model, String color, Integer manufactureYear) {
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(customer);
        vehicle.setLicensePlate(licensePlate);
        vehicle.setBrand(brand);
        vehicle.setModel(model);
        vehicle.setColor(color);
        vehicle.setManufactureYear(manufactureYear);
        return vehicleRepository.save(vehicle);
    }

    private Booking createDemoBookingIfMissing(
            String marker,
            User customer,
            Vehicle vehicle,
            User staff,
            List<CarWashService> services,
            LocalDateTime scheduledAt,
            BookingStatus status,
            BookingPaymentStatus paymentStatus,
            LocalDateTime completedAt) {
        return bookingRepository.findAll().stream()
                .filter(booking -> marker.equals(booking.getNote()))
                .findFirst()
                .orElseGet(() -> createDemoBooking(
                        marker, customer, vehicle, staff, services, scheduledAt, status, paymentStatus, completedAt));
    }

    private Booking createDemoBooking(
            String marker,
            User customer,
            Vehicle vehicle,
            User staff,
            List<CarWashService> services,
            LocalDateTime scheduledAt,
            BookingStatus status,
            BookingPaymentStatus paymentStatus,
            LocalDateTime completedAt) {
        BigDecimal subtotal = services.stream()
                .map(CarWashService::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setVehicle(vehicle);
        booking.setAssignedStaff(staff);
        booking.setScheduledAt(scheduledAt);
        booking.setStatus(status);
        booking.setSubtotalAmount(subtotal);
        booking.setDiscountAmount(BigDecimal.ZERO);
        booking.setFinalAmount(subtotal);
        booking.setPaymentMethod(BookingPaymentMethod.CASH);
        booking.setPaymentStatus(paymentStatus);
        booking.setPaidAt(paymentStatus == BookingPaymentStatus.PAID ? completedAt : null);
        booking.setCompletedAt(completedAt);
        booking.setEarnedPoints(status == BookingStatus.COMPLETED ? subtotal.divide(BigDecimal.valueOf(10000)).intValue() : 0);
        booking.setNote(marker);
        services.forEach(service -> addDemoBookingService(booking, service));
        return bookingRepository.save(booking);
    }

    private void addDemoBookingService(Booking booking, CarWashService service) {
        BookingService item = new BookingService();
        item.setService(service);
        item.setServiceName(service.getName());
        item.setPrice(service.getPrice());
        item.setDurationMinutes(service.getDurationMinutes());
        booking.addService(item);
    }

    private void seedDemoLoyalty(User customer, Booking booking) {
        LoyaltyAccount account = loyaltyAccountRepository.findByCustomer(customer).orElse(null);
        if (account == null) {
            return;
        }
        int points = booking == null ? 120 : Math.max(booking.getEarnedPoints(), 120);
        if (account.getCurrentPoints() < points) {
            account.setCurrentPoints(points);
            account.setLifetimePoints(Math.max(account.getLifetimePoints(), points));
            account.setTotalSpending(account.getTotalSpending().max(new BigDecimal("120000")));
            account.setVisitCount(Math.max(account.getVisitCount(), 1));
            loyaltyAccountRepository.save(account);
        }
        String description = "Tích điểm sau khi hoàn thành dịch vụ";
        boolean exists = loyaltyTransactionRepository.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .anyMatch(transaction -> description.equals(transaction.getDescription()));
        if (!exists) {
            LoyaltyTransaction transaction = new LoyaltyTransaction();
            transaction.setCustomer(customer);
            transaction.setBooking(booking);
            transaction.setType(LoyaltyTransactionType.EARN);
            transaction.setPoints(points);
            transaction.setDescription(description);
            transaction.setExpiresAt(LocalDateTime.now().plusMonths(12));
            loyaltyTransactionRepository.save(transaction);
        }
    }
}
