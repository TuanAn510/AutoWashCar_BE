package com.shinecraft.server.loyalty;

import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoyaltyService {
    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final MembershipTierRepository tierRepository;
    private final RewardRepository rewardRepository;
    private final RewardRedemptionRepository redemptionRepository;
    private final CarWashServiceRepository carWashServiceRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final int pointExpiryMonths;

    public LoyaltyService(
            LoyaltyAccountRepository accountRepository,
            LoyaltyTransactionRepository transactionRepository,
            MembershipTierRepository tierRepository,
            RewardRepository rewardRepository,
            RewardRedemptionRepository redemptionRepository,
            CarWashServiceRepository carWashServiceRepository,
            UserRepository userRepository,
            AuthService authService,
            @Value("${app.loyalty.point-expiry-months:12}") int pointExpiryMonths) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.tierRepository = tierRepository;
        this.rewardRepository = rewardRepository;
        this.redemptionRepository = redemptionRepository;
        this.carWashServiceRepository = carWashServiceRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.pointExpiryMonths = pointExpiryMonths;
    }

    public List<LoyaltyDtos.TierResponse> tiers() {
        return tierRepository.findByIsActiveTrueOrderByMinPointsAsc().stream()
                .map(LoyaltyDtos.TierResponse::from)
                .toList();
    }

    @Transactional
    public LoyaltyDtos.TierResponse saveTier(Long id, LoyaltyDtos.TierRequest request) {
        MembershipTier tier = id == null
                ? new MembershipTier()
                : tierRepository
                        .findById(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Khong tim thay hang thanh vien"));
        tier.setName(request.name().trim());
        tier.setMinPoints(request.minPoints());
        tier.setDiscountPercent(request.discountPercent());
        tier.setBookingWindowDays(request.bookingWindowDays());
        tier.setPriorityLevel(request.priorityLevel());
        tier.setDescription(request.description());
        if (request.active() != null) {
            tier.setActive(request.active());
        }
        return LoyaltyDtos.TierResponse.from(tierRepository.save(tier));
    }

    public LoyaltyDtos.LoyaltyAccountResponse myAccount() {
        return LoyaltyDtos.LoyaltyAccountResponse.from(getOrCreateAccount(authService.currentUser()));
    }

    public List<LoyaltyDtos.TransactionResponse> myTransactions() {
        return transactionRepository.findByCustomerOrderByCreatedAtDesc(authService.currentUser()).stream()
                .map(LoyaltyDtos.TransactionResponse::from)
                .toList();
    }

    public List<LoyaltyDtos.RewardResponse> rewards() {
        return rewardRepository.findByIsActiveTrueOrderByRequiredPointsAsc().stream()
                .map(LoyaltyDtos.RewardResponse::from)
                .toList();
    }

    @Transactional
    public LoyaltyDtos.RewardResponse saveReward(Long id, LoyaltyDtos.RewardRequest request) {
        Reward reward = id == null
                ? new Reward()
                : rewardRepository
                        .findById(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Khong tim thay reward"));
        reward.setName(request.name().trim());
        reward.setDescription(request.description());
        reward.setRequiredPoints(request.requiredPoints());
        reward.setRewardType(request.rewardType());
        reward.setDiscountAmount(request.discountAmount());
        if (request.addOnServiceId() != null) {
            CarWashService service = carWashServiceRepository
                    .findById(request.addOnServiceId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Khong tim thay dich vu add-on"));
            reward.setAddOnService(service);
        } else {
            reward.setAddOnService(null);
        }
        if (request.active() != null) {
            reward.setActive(request.active());
        }
        return LoyaltyDtos.RewardResponse.from(rewardRepository.save(reward));
    }

    @Transactional
    public LoyaltyDtos.RedemptionResponse redeem(Long rewardId) {
        User customer = authService.currentUser();
        Reward reward = rewardRepository
                .findById(rewardId)
                .filter(Reward::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reward khong kha dung"));
        LoyaltyAccount account = getOrCreateAccount(customer);
        if (account.getCurrentPoints() < reward.getRequiredPoints()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Khong du diem de doi reward");
        }

        account.setCurrentPoints(account.getCurrentPoints() - reward.getRequiredPoints());
        accountRepository.save(account);

        RewardRedemption redemption = new RewardRedemption();
        redemption.setCustomer(customer);
        redemption.setReward(reward);
        redemption.setCode("RW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        redemption.setPointsUsed(reward.getRequiredPoints());
        redemption.setRedeemedAt(LocalDateTime.now());
        redemption.setExpiresAt(LocalDateTime.now().plusMonths(pointExpiryMonths));
        redemption = redemptionRepository.save(redemption);

        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setCustomer(customer);
        transaction.setRedemption(redemption);
        transaction.setType(LoyaltyTransactionType.REDEEM);
        transaction.setPoints(-reward.getRequiredPoints());
        transaction.setDescription("Doi reward " + reward.getName());
        transactionRepository.save(transaction);

        return LoyaltyDtos.RedemptionResponse.from(redemption);
    }

    public List<LoyaltyDtos.RedemptionResponse> myRedemptions() {
        return redemptionRepository.findByCustomerOrderByRedeemedAtDesc(authService.currentUser()).stream()
                .map(LoyaltyDtos.RedemptionResponse::from)
                .toList();
    }

    @Transactional
    public LoyaltyAccount getOrCreateAccount(User customer) {
        return accountRepository.findByCustomer(customer).orElseGet(() -> {
            LoyaltyAccount account = new LoyaltyAccount();
            account.setCustomer(customer);
            account.setMembershipTier(findTier(0));
            return accountRepository.save(account);
        });
    }

    @Transactional
    public void earnPoints(User customer, BigDecimal amount, int points, String description, Object booking) {
        LoyaltyAccount account = getOrCreateAccount(customer);
        account.setCurrentPoints(account.getCurrentPoints() + points);
        account.setLifetimePoints(account.getLifetimePoints() + points);
        account.setTotalSpending(account.getTotalSpending().add(amount));
        account.setVisitCount(account.getVisitCount() + 1);
        account.setMembershipTier(findTier(account.getLifetimePoints()));
        accountRepository.save(account);

        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setCustomer(customer);
        if (booking instanceof com.shinecraft.server.booking.Booking b) {
            transaction.setBooking(b);
        }
        transaction.setType(LoyaltyTransactionType.EARN);
        transaction.setPoints(points);
        transaction.setDescription(description);
        transaction.setExpiresAt(LocalDateTime.now().plusMonths(pointExpiryMonths));
        transactionRepository.save(transaction);
    }

    @Scheduled(cron = "0 0 2 1 * *")
    @Transactional
    public void monthlyReviewAndExpiry() {
        LocalDateTime now = LocalDateTime.now();
        expireOldPoints(now);
        userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_CUSTOMER).forEach(customer -> {
            LoyaltyAccount account = getOrCreateAccount(customer);
            account.setMembershipTier(findTier(account.getLifetimePoints()));
            account.setLastReviewedAt(now);
        });
    }

    @Transactional
    public int expireOldPoints(LocalDateTime now) {
        List<LoyaltyTransaction> expiredEarns = transactionRepository
                .findByTypeAndExpiresAtBeforeAndPointsGreaterThan(LoyaltyTransactionType.EARN, now, 0);
        int expiredTotal = 0;
        for (LoyaltyTransaction earn : expiredEarns) {
            LoyaltyAccount account = getOrCreateAccount(earn.getCustomer());
            int expired = Math.min(account.getCurrentPoints(), earn.getPoints());
            if (expired <= 0) {
                continue;
            }
            account.setCurrentPoints(account.getCurrentPoints() - expired);
            LoyaltyTransaction transaction = new LoyaltyTransaction();
            transaction.setCustomer(earn.getCustomer());
            transaction.setType(LoyaltyTransactionType.EXPIRE);
            transaction.setPoints(-expired);
            transaction.setDescription("Het han diem sau " + pointExpiryMonths + " thang");
            transactionRepository.save(transaction);
            earn.setPoints(0);
            expiredTotal += expired;
        }
        return expiredTotal;
    }

    private MembershipTier findTier(Integer points) {
        return tierRepository
                .findFirstByIsActiveTrueAndMinPointsLessThanEqualOrderByMinPointsDesc(points)
                .orElse(null);
    }
}
