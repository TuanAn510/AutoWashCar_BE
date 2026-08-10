package com.shinecraft.server.config;

import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.catalog.ServiceCategory;
import com.shinecraft.server.catalog.ServiceCategoryRepository;
import com.shinecraft.server.loyalty.MembershipTier;
import com.shinecraft.server.loyalty.MembershipTierRepository;
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
    private final ServiceCategoryRepository categoryRepository;
    private final CarWashServiceRepository serviceRepository;
    private final RewardRepository rewardRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPhone;
    private final String adminPassword;

    public DataSeeder(
            UserRepository userRepository,
            MembershipTierRepository tierRepository,
            ServiceCategoryRepository categoryRepository,
            CarWashServiceRepository serviceRepository,
            RewardRepository rewardRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.seed-phone}") String adminPhone,
            @Value("${app.admin.seed-password}") String adminPassword) {
        this.userRepository = userRepository;
        this.tierRepository = tierRepository;
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.rewardRepository = rewardRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedTiers();
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
