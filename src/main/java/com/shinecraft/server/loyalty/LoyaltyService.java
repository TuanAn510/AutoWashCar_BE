package com.shinecraft.server.loyalty;

import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserDtos;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoyaltyService {
    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);
    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final MembershipTierRepository tierRepository;
    private final RewardRepository rewardRepository;
    private final RewardRedemptionRepository redemptionRepository;
    private final PointLotRepository pointLotRepository;
    private final LoyaltyMonthlySnapshotRepository snapshotRepository;
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
            PointLotRepository pointLotRepository,
            LoyaltyMonthlySnapshotRepository snapshotRepository,
            CarWashServiceRepository carWashServiceRepository,
            UserRepository userRepository,
            AuthService authService,
            @Value("${app.loyalty.point-expiry-months:12}") int pointExpiryMonths) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.tierRepository = tierRepository;
        this.rewardRepository = rewardRepository;
        this.redemptionRepository = redemptionRepository;
        this.pointLotRepository = pointLotRepository;
        this.snapshotRepository = snapshotRepository;
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
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Membership tier not found"));
        tier.setName(request.name().trim());
        tier.setMinPoints(request.resolvedMinPoints() == null ? 0 : request.resolvedMinPoints());
        tier.setDiscountPercent(request.discountPercent() == null ? BigDecimal.ZERO : request.discountPercent());
        tier.setBookingWindowDays(request.bookingWindowDays() == null ? 7 : request.bookingWindowDays());
        tier.setPriorityLevel(request.priorityLevel() == null ? 0 : request.priorityLevel());
        tier.setDescription(request.description());
        if (request.resolvedActive() != null) {
            tier.setActive(request.resolvedActive());
        }
        return LoyaltyDtos.TierResponse.from(tierRepository.save(tier));
    }

    @Transactional
    public LoyaltyDtos.TierResponse deactivateTier(Long id) {
        MembershipTier tier = tierRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Membership tier not found"));
        tier.setActive(false);
        return LoyaltyDtos.TierResponse.from(tierRepository.save(tier));
    }

    @Transactional
    public LoyaltyDtos.LoyaltyAccountResponse myAccount() {
        return accountResponse(getOrCreateAccount(authService.currentUser()));
    }

    @Transactional
    public List<Map<String, Object>> customersWithLoyalty(String search) {
        String keyword = search == null ? "" : search.trim().toLowerCase();
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ROLE_CUSTOMER)
                .filter(user -> keyword.isBlank()
                        || user.getFullName().toLowerCase().contains(keyword)
                        || user.getPhone().contains(keyword))
                .map(user -> {
                    Map<String, Object> customer = new LinkedHashMap<>();
                    customer.put("_id", String.valueOf(user.getId()));
                    customer.put("displayName", user.getFullName());
                    customer.put("phone", user.getPhone());
                    customer.put("role", UserDtos.toFrontendRole(user.getRole()));
                    customer.put("isActive", user.isActive());
                    customer.put("createdAt", user.getCreatedAt());
                    customer.put("updatedAt", user.getUpdatedAt());

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("customer", customer);
                    item.put("loyaltyAccount", accountResponse(getOrCreateAccount(user)));
                    return item;
                })
                .toList();
    }

    @Transactional
    public LoyaltyDtos.LoyaltyAccountResponse customerAccount(Long customerId) {
        User customer = userRepository
                .findById(customerId)
                .filter(user -> user.getRole() == UserRole.ROLE_CUSTOMER)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found"));
        return accountResponse(getOrCreateAccount(customer));
    }

    @Transactional(readOnly = true)
    public List<LoyaltyDtos.TransactionResponse> customerTransactions(Long customerId) {
        User customer = userRepository
                .findById(customerId)
                .filter(user -> user.getRole() == UserRole.ROLE_CUSTOMER)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found"));
        return transactionRepository.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .map(LoyaltyDtos.TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoyaltyDtos.TransactionResponse> myTransactions() {
        return transactionRepository.findByCustomerOrderByCreatedAtDesc(authService.currentUser()).stream()
                .map(LoyaltyDtos.TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoyaltyDtos.RewardResponse> rewards() {
        return rewardRepository.findByIsActiveTrueOrderByRequiredPointsAsc().stream()
                .map(this::rewardResponse)
                .toList();
    }

    @Transactional
    public LoyaltyDtos.RewardResponse saveReward(Long id, LoyaltyDtos.RewardRequest request) {
        Reward reward = id == null
                ? new Reward()
                : rewardRepository
                        .findById(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reward not found"));
        reward.setName(request.name().trim());
        reward.setDescription(request.description());
        reward.setRequiredPoints(request.requiredPoints());
        reward.setRewardType(request.resolvedRewardType());
        reward.setDiscountAmount(request.resolvedDiscountAmount());
        reward.setMinOrderAmount(request.minOrderAmount());
        reward.setMaxDiscountAmount(request.maxDiscountAmount());
        reward.setQuantity(request.quantity());
        reward.setExpiredAt(parseExpiredAt(request.expiredAt()));
        if (request.addOnServiceId() != null) {
            CarWashService service = carWashServiceRepository
                    .findById(request.addOnServiceId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Add-on service not found"));
            reward.setAddOnService(service);
        } else {
            reward.setAddOnService(null);
        }
        if (request.resolvedActive() != null) {
            reward.setActive(request.resolvedActive());
        }
        return rewardResponse(rewardRepository.save(reward));
    }

    @Transactional
    public LoyaltyDtos.RewardResponse deactivateReward(Long id) {
        Reward reward = rewardRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reward not found"));
        reward.setActive(false);
        return rewardResponse(rewardRepository.save(reward));
    }

    @Transactional
    public LoyaltyDtos.RedemptionResponse redeem(Long rewardId) {
        User customer = authService.currentUser();
        Reward reward = rewardRepository
                .findById(rewardId)
                .filter(Reward::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reward is not available"));
        LoyaltyAccount account = getOrCreateAccountForUpdate(customer);
        validateRewardRedeemable(customer, reward);
        if (account.getCurrentPoints() < reward.getRequiredPoints()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Not enough points to redeem this reward");
        }

        useOldestPointLots(customer, reward.getRequiredPoints());
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
        transaction.setDescription("Redeemed reward " + reward.getName());
        transactionRepository.save(transaction);

        return LoyaltyDtos.RedemptionResponse.from(redemption);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyDtos.RedemptionResponse> myRedemptions() {
        return redemptionRepository.findByCustomerOrderByRedeemedAtDesc(authService.currentUser()).stream()
                .map(LoyaltyDtos.RedemptionResponse::from)
                .toList();
    }

    @Transactional
    public LoyaltyDtos.RedemptionResponse markRedemptionUsed(Long redemptionId) {
        RewardRedemption redemption = redemptionRepository
                .findById(redemptionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reward redemption not found"));
        if (redemption.getStatus() != RewardRedemptionStatus.AVAILABLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reward redemption is not available");
        }
        if (redemption.getExpiresAt() != null && !redemption.getExpiresAt().isAfter(LocalDateTime.now())) {
            redemption.setStatus(RewardRedemptionStatus.EXPIRED);
            redemptionRepository.save(redemption);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reward redemption has expired");
        }
        redemption.setStatus(RewardRedemptionStatus.USED);
        redemption.setUsedAt(LocalDateTime.now());
        return LoyaltyDtos.RedemptionResponse.from(redemptionRepository.save(redemption));
    }

    @Transactional
    public LoyaltyAccount getOrCreateAccount(User customer) {
        LoyaltyAccount account = accountRepository.findByCustomer(customer).orElseGet(() -> {
            LoyaltyAccount newAccount = new LoyaltyAccount();
            newAccount.setCustomer(customer);
            newAccount.setMembershipTier(findTier(0));
            return accountRepository.saveAndFlush(newAccount);
        });

        // Auto-upgrade tier on every access (handles points earned before upgrade logic existed)
        MembershipTier currentTier = account.getMembershipTier();
        MembershipTier expectedTier = findTier(account.getLifetimePoints());
        log.info("Auto-upgrade check for customer {}: lifetimePoints={}, currentTier={}, expectedTier={}",
                customer.getId(), account.getLifetimePoints(),
                currentTier != null ? currentTier.getName() : "none",
                expectedTier != null ? expectedTier.getName() : "none");
        if (expectedTier != null && (currentTier == null
                || expectedTier.getPriorityLevel() > currentTier.getPriorityLevel())) {
            account.setMembershipTier(expectedTier);
            accountRepository.updateMembershipTier(account.getId(), expectedTier);
            log.info("Auto-upgraded customer {} from {} to {} on account access",
                    customer.getId(),
                    currentTier != null ? currentTier.getName() : "none",
                    expectedTier.getName());
        }

        return account;
    }

    @Transactional
    public void earnPoints(User customer, BigDecimal amount, int points, String description, Object booking) {
        LocalDateTime now = LocalDateTime.now();
        LoyaltyAccount account = getOrCreateAccountForUpdate(customer);
        account.setCurrentPoints(account.getCurrentPoints() + points);
        account.setLifetimePoints(account.getLifetimePoints() + points);
        account.setTotalSpending(account.getTotalSpending().add(amount));
        account.setVisitCount(account.getVisitCount() + 1);

        // Auto-upgrade membership tier based on lifetime points
        MembershipTier newTier = findTier(account.getLifetimePoints());
        MembershipTier currentTier = account.getMembershipTier();
        log.info("Earned {} points for customer {}. Current points: {}, current tier: {}, new tier: {}",
                points, customer.getId(), account.getCurrentPoints(),
                currentTier != null ? currentTier.getName() : "none",
                newTier != null ? newTier.getName() : "none");
        if (newTier != null && (currentTier == null
                || newTier.getPriorityLevel() > currentTier.getPriorityLevel())) {
            account.setMembershipTier(newTier);
            log.info("Upgraded customer {} from {} to {}",
                    customer.getId(),
                    currentTier != null ? currentTier.getName() : "none",
                    newTier.getName());
        }
        accountRepository.saveAndFlush(account);

        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setCustomer(customer);
        if (booking instanceof com.shinecraft.server.booking.Booking b) {
            transaction.setBooking(b);
        }
        transaction.setType(LoyaltyTransactionType.EARN);
        transaction.setPoints(points);
        transaction.setDescription(description);
        transaction.setExpiresAt(now.plusMonths(pointExpiryMonths));
        transaction = transactionRepository.save(transaction);

        PointLot lot = new PointLot();
        lot.setCustomer(customer);
        lot.setEarnTransaction(transaction);
        lot.setInitialPoints(points);
        lot.setRemainingPoints(points);
        lot.setEarnedAt(now);
        lot.setExpiresAt(transaction.getExpiresAt());
        pointLotRepository.save(lot);
    }

    @Scheduled(cron = "0 0 2 1 * *")
    @Transactional
    public void monthlyReviewAndExpiry() {
        LocalDateTime now = LocalDateTime.now();
        expireOldPoints(now);
        expireOldRedemptions(now);
        LocalDate periodStart = now.toLocalDate().withDayOfMonth(1);
        LocalDateTime reviewSince = now.minusMonths(pointExpiryMonths);
        userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_CUSTOMER).forEach(customer -> {
            LoyaltyAccount account = getOrCreateAccountForUpdate(customer);
            MembershipTier tierBefore = account.getMembershipTier();
            int reviewPoints = Math.toIntExact(transactionRepository.sumEarnedPointsSince(customer, reviewSince));
            BigDecimal reviewSpending = transactionRepository.sumEarnedSpendingSince(customer, reviewSince);
            Long reviewVisits = transactionRepository.countEarnVisitsSince(customer, reviewSince);
            MembershipTier tierAfter = findTier(reviewPoints);
            account.setMembershipTier(tierAfter);
            account.setLastReviewedAt(now);
            saveMonthlySnapshot(customer, periodStart, now, reviewPoints, reviewSpending, reviewVisits, tierBefore, tierAfter);
        });
    }

    @Transactional
    public int expireOldPoints(LocalDateTime now) {
        List<PointLot> expiredLots =
                pointLotRepository.findByExpiresAtBeforeAndRemainingPointsGreaterThanOrderByExpiresAtAscIdAsc(now, 0);
        int expiredTotal = 0;
        for (PointLot lot : expiredLots) {
            LoyaltyAccount account = getOrCreateAccountForUpdate(lot.getCustomer());
            int expired = Math.min(account.getCurrentPoints(), lot.getRemainingPoints());
            if (expired <= 0) {
                continue;
            }
            account.setCurrentPoints(account.getCurrentPoints() - expired);
            LoyaltyTransaction transaction = new LoyaltyTransaction();
            transaction.setCustomer(lot.getCustomer());
            transaction.setType(LoyaltyTransactionType.EXPIRE);
            transaction.setPoints(-expired);
            transaction.setDescription("Points expired after " + pointExpiryMonths + " months");
            transactionRepository.save(transaction);
            lot.setRemainingPoints(lot.getRemainingPoints() - expired);
            expiredTotal += expired;
        }
        return expiredTotal;
    }

    @Transactional
    public int expireOldRedemptions(LocalDateTime now) {
        List<RewardRedemption> expiredRedemptions =
                redemptionRepository.findByStatusAndExpiresAtBefore(RewardRedemptionStatus.AVAILABLE, now);
        expiredRedemptions.forEach(redemption -> redemption.setStatus(RewardRedemptionStatus.EXPIRED));
        return expiredRedemptions.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRedemptionExpired(Long redemptionId) {
        redemptionRepository.findById(redemptionId)
                .filter(redemption -> redemption.getStatus() == RewardRedemptionStatus.AVAILABLE)
                .ifPresent(redemption -> redemption.setStatus(RewardRedemptionStatus.EXPIRED));
    }

    private LoyaltyAccount getOrCreateAccountForUpdate(User customer) {
        return accountRepository.findByCustomer(customer).orElseGet(() -> {
            LoyaltyAccount account = new LoyaltyAccount();
            account.setCustomer(customer);
            account.setMembershipTier(findTier(0));
            return accountRepository.saveAndFlush(account);
        });
    }

    private LoyaltyDtos.LoyaltyAccountResponse accountResponse(LoyaltyAccount account) {
        QuarterWindow quarter = currentQuarter(LocalDate.now());
        User customer = account.getCustomer();
        int totalRedeemed = positivePoints(customer, LoyaltyTransactionType.REDEEM);
        int totalExpired = positivePoints(customer, LoyaltyTransactionType.EXPIRE);
        int currentQuarterEarned = Math.toIntExact(transactionRepository.sumEarnedPointsBetween(
                customer, quarter.start().atStartOfDay(), quarter.nextStart().atStartOfDay()));
        return LoyaltyDtos.LoyaltyAccountResponse.from(
                account,
                totalRedeemed,
                totalExpired,
                currentQuarterEarned,
                quarter.periodKey(),
                quarter.nextStart().atStartOfDay(),
                transactionRepository.lastEarnedAt(customer));
    }

    private int positivePoints(User customer, LoyaltyTransactionType type) {
        long total = transactionRepository.sumPointsByType(customer, type);
        return Math.toIntExact(Math.abs(total));
    }

    private LoyaltyDtos.RewardResponse rewardResponse(Reward reward) {
        User currentUser = authService.currentUser();
        boolean hasRedeemed = currentUser.getRole() == UserRole.ROLE_CUSTOMER
                && redemptionRepository.existsByCustomerAndReward(currentUser, reward);
        return LoyaltyDtos.RewardResponse.from(reward, redemptionRepository.countByReward(reward), hasRedeemed);
    }

    private void validateRewardRedeemable(User customer, Reward reward) {
        LocalDateTime now = LocalDateTime.now();
        if (reward.getExpiredAt() != null && !reward.getExpiredAt().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reward has expired");
        }
        if (reward.getQuantity() != null && redemptionRepository.countByReward(reward) >= reward.getQuantity()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reward is out of stock");
        }
        if (redemptionRepository.existsByCustomerAndReward(customer, reward)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reward has already been redeemed");
        }
    }

    private LocalDateTime parseExpiredAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Frontend date inputs may arrive as ISO timestamps with an offset.
        }
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Date-only payloads are accepted as end-of-day expirations.
        }
        try {
            return LocalDate.parse(trimmed).atTime(LocalTime.MAX);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reward expiration date is invalid");
        }
    }

    private QuarterWindow currentQuarter(LocalDate date) {
        int quarterIndex = (date.getMonthValue() - 1) / 3;
        int quarterStartMonth = quarterIndex * 3 + 1;
        LocalDate start = LocalDate.of(date.getYear(), quarterStartMonth, 1);
        LocalDate nextStart = start.plusMonths(3);
        return new QuarterWindow(start, nextStart, date.getYear() + "-Q" + (quarterIndex + 1));
    }

    private record QuarterWindow(LocalDate start, LocalDate nextStart, String periodKey) {}

    private void useOldestPointLots(User customer, int points) {
        int remaining = points;
        List<PointLot> lots =
                pointLotRepository.findByCustomerAndRemainingPointsGreaterThanOrderByExpiresAtAscIdAsc(customer, 0);
        for (PointLot lot : lots) {
            if (remaining <= 0) {
                break;
            }
            int used = Math.min(remaining, lot.getRemainingPoints());
            lot.setRemainingPoints(lot.getRemainingPoints() - used);
            remaining -= used;
        }
        if (remaining > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Loyalty points changed. Please try again");
        }
    }

    private void saveMonthlySnapshot(
            User customer,
            LocalDate periodStart,
            LocalDateTime reviewedAt,
            int reviewPoints,
            BigDecimal reviewSpending,
            Long reviewVisits,
            MembershipTier tierBefore,
            MembershipTier tierAfter) {
        LoyaltyMonthlySnapshot snapshot = snapshotRepository
                .findByCustomerAndPeriodStart(customer, periodStart)
                .orElseGet(LoyaltyMonthlySnapshot::new);
        snapshot.setCustomer(customer);
        snapshot.setPeriodStart(periodStart);
        snapshot.setReviewPoints(reviewPoints);
        snapshot.setReviewSpending(reviewSpending == null ? BigDecimal.ZERO : reviewSpending);
        snapshot.setReviewVisits(reviewVisits == null ? 0L : reviewVisits);
        snapshot.setTierBefore(tierBefore);
        snapshot.setTierAfter(tierAfter);
        snapshot.setReviewedAt(reviewedAt);
        snapshotRepository.save(snapshot);
    }

    private MembershipTier findTier(Integer points) {
        return tierRepository
                .findFirstByIsActiveTrueAndMinPointsLessThanEqualOrderByMinPointsDesc(points)
                .orElse(null);
    }
}
