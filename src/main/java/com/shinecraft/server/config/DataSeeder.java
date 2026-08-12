package com.shinecraft.server.config;

import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.catalog.ServiceCategory;
import com.shinecraft.server.catalog.ServiceCategoryRepository;
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
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final MembershipTierRepository tierRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final CarWashServiceRepository serviceRepository;
    private final RewardRepository rewardRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPhone;
    private final String adminPassword;
    private final String staffPhone;
    private final String staffPassword;
    private final String customerOnePhone;
    private final String customerTwoPhone;
    private final String customerThreePhone;
    private final String customerPassword;

    public DataSeeder(
            UserRepository userRepository,
            MembershipTierRepository tierRepository,
            LoyaltyAccountRepository loyaltyAccountRepository,
            ServiceCategoryRepository categoryRepository,
            CarWashServiceRepository serviceRepository,
            RewardRepository rewardRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.seed-phone}") String adminPhone,
            @Value("${app.admin.seed-password}") String adminPassword,
            @Value("${app.staff.seed-phone:0987654321}") String staffPhone,
            @Value("${app.staff.seed-password:Staff@123456}") String staffPassword,
            @Value("${app.customer-one.seed-phone:0911111111}") String customerOnePhone,
            @Value("${app.customer-two.seed-phone:0922222222}") String customerTwoPhone,
            @Value("${app.customer-three.seed-phone:0933333333}") String customerThreePhone,
            @Value("${app.customer.seed-password:Customer@123456}") String customerPassword) {
        this.userRepository = userRepository;
        this.tierRepository = tierRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.rewardRepository = rewardRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
        this.staffPhone = staffPhone;
        this.staffPassword = staffPassword;
        this.customerOnePhone = customerOnePhone;
        this.customerTwoPhone = customerTwoPhone;
        this.customerThreePhone = customerThreePhone;
        this.customerPassword = customerPassword;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedStaff();
        seedTiers();
        seedCustomers();
        seedCatalog();
        seedRewards();
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

        createService(wash, "Basic Wash", "Exterior cleaning", "70000", 30);
        createService(wash, "Premium Wash", "Exterior wash and interior vacuum", "120000", 45);
        createService(detailing, "Interior Cleaning", "Deep interior cleaning", "250000", 90);
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
}
