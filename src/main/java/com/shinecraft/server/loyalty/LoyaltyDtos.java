package com.shinecraft.server.loyalty;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class LoyaltyDtos {
    private LoyaltyDtos() {}

    public record TierRequest(
            @NotBlank @Size(max = 80) String name,
            @Min(0) Integer minPoints,
            @Min(0) Integer minTotalEarnedPoints,
            @DecimalMin("0.0") BigDecimal discountPercent,
            @Min(1) Integer bookingWindowDays,
            @Min(0) Integer priorityLevel,
            @Size(max = 1000) String description,
            Boolean active,
            Boolean isActive) {
        public Integer resolvedMinPoints() {
            return minTotalEarnedPoints != null ? minTotalEarnedPoints : minPoints;
        }

        public Boolean resolvedActive() {
            return isActive != null ? isActive : active;
        }
    }

    public record TierResponse(
            Long id,
            @JsonProperty("_id") String uid,
            String name,
            Integer minPoints,
            Integer minTotalEarnedPoints,
            BigDecimal discountPercent,
            Integer bookingWindowDays,
            Integer priorityLevel,
            String description,
            boolean active,
            boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public static TierResponse from(MembershipTier tier) {
            return new TierResponse(
                    tier.getId(),
                    String.valueOf(tier.getId()),
                    tier.getName(),
                    tier.getMinPoints(),
                    tier.getMinPoints(),
                    tier.getDiscountPercent(),
                    tier.getBookingWindowDays(),
                    tier.getPriorityLevel(),
                    tier.getDescription(),
                    tier.isActive(),
                    tier.isActive(),
                    tier.getCreatedAt(),
                    tier.getUpdatedAt());
        }
    }

    public record LoyaltyAccountResponse(
            @JsonProperty("_id") String uid,
            String customerId,
            Integer currentPoints,
            Integer lifetimePoints,
            Integer totalEarnedPoints,
            Integer totalRedeemedPoints,
            Integer totalExpiredPoints,
            Integer currentQuarterEarnedPoints,
            String loyaltyPeriodKey,
            LocalDateTime nextQuarterResetAt,
            BigDecimal totalSpending,
            Integer visitCount,
            TierResponse tier,
            TierResponse membershipTierId,
            LocalDateTime lastPointEarnedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public static LoyaltyAccountResponse from(LoyaltyAccount account) {
            return from(account, 0, 0, account.getLifetimePoints(), null, null, null);
        }

        public static LoyaltyAccountResponse from(
                LoyaltyAccount account,
                Integer totalRedeemedPoints,
                Integer totalExpiredPoints,
                Integer currentQuarterEarnedPoints,
                String loyaltyPeriodKey,
                LocalDateTime nextQuarterResetAt,
                LocalDateTime lastPointEarnedAt) {
            return new LoyaltyAccountResponse(
                    String.valueOf(account.getId()),
                    String.valueOf(account.getCustomer().getId()),
                    account.getCurrentPoints(),
                    account.getLifetimePoints(),
                    account.getLifetimePoints(),
                    totalRedeemedPoints == null ? 0 : totalRedeemedPoints,
                    totalExpiredPoints == null ? 0 : totalExpiredPoints,
                    currentQuarterEarnedPoints == null ? 0 : currentQuarterEarnedPoints,
                    loyaltyPeriodKey,
                    nextQuarterResetAt,
                    account.getTotalSpending(),
                    account.getVisitCount(),
                    account.getMembershipTier() == null ? null : TierResponse.from(account.getMembershipTier()),
                    account.getMembershipTier() == null ? null : TierResponse.from(account.getMembershipTier()),
                    lastPointEarnedAt,
                    account.getCreatedAt(),
                    account.getUpdatedAt());
        }
    }

    public record TransactionResponse(
            Long id,
            @JsonProperty("_id") String uid,
            String type,
            String status,
            Integer points,
            Integer remainingPoints,
            String description,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        public static TransactionResponse from(LoyaltyTransaction transaction) {
            return new TransactionResponse(
                    transaction.getId(),
                    String.valueOf(transaction.getId()),
                    transaction.getType().name().toLowerCase(),
                    transaction.getStatus().name().toLowerCase(),
                    transaction.getPoints(),
                    null,
                    transaction.getDescription(),
                    transaction.getExpiresAt(),
                    effectiveTransactionTime(transaction));
        }

        public static TransactionResponse from(LoyaltyTransaction transaction, Integer remainingPoints) {
            return new TransactionResponse(
                    transaction.getId(),
                    String.valueOf(transaction.getId()),
                    transaction.getType().name().toLowerCase(),
                    transaction.getStatus().name().toLowerCase(),
                    transaction.getPoints(),
                    remainingPoints,
                    transaction.getDescription(),
                    transaction.getExpiresAt(),
                    effectiveTransactionTime(transaction));
        }

        private static LocalDateTime effectiveTransactionTime(LoyaltyTransaction transaction) {
            return transaction.getPostedAt() == null ? transaction.getCreatedAt() : transaction.getPostedAt();
        }
    }

    public record RewardRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @Min(1) Integer requiredPoints,
            RewardType rewardType,
            String discountType,
            @DecimalMin("0.0") BigDecimal discountAmount,
            @DecimalMin("0.0") BigDecimal discountValue,
            @DecimalMin("0.0") BigDecimal minOrderAmount,
            @DecimalMin("0.0") BigDecimal maxDiscountAmount,
            @Min(0) Integer quantity,
            String expiredAt,
            Long addOnServiceId,
            Boolean active,
            Boolean isActive) {
        public RewardType resolvedRewardType() {
            return rewardType != null ? rewardType : RewardType.DISCOUNT_CODE;
        }

        public BigDecimal resolvedDiscountAmount() {
            return discountValue != null ? discountValue : discountAmount;
        }

        public Boolean resolvedActive() {
            return isActive != null ? isActive : active;
        }
    }

    public record RewardResponse(
            Long id,
            @JsonProperty("_id") String uid,
            String name,
            String description,
            Integer requiredPoints,
            RewardType rewardType,
            String discountType,
            BigDecimal discountAmount,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            Integer quantity,
            Long redeemedCount,
            Boolean hasRedeemed,
            LocalDateTime expiredAt,
            Long addOnServiceId,
            boolean active,
            boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public static RewardResponse from(Reward reward) {
            return from(reward, 0L, false);
        }

        public static RewardResponse from(Reward reward, Long redeemedCount, Boolean hasRedeemed) {
            return new RewardResponse(
                    reward.getId(),
                    String.valueOf(reward.getId()),
                    reward.getName(),
                    reward.getDescription(),
                    reward.getRequiredPoints(),
                    reward.getRewardType(),
                    "fixed_amount",
                    reward.getDiscountAmount(),
                    reward.getDiscountAmount(),
                    reward.getMinOrderAmount(),
                    reward.getMaxDiscountAmount(),
                    reward.getQuantity(),
                    redeemedCount == null ? 0L : redeemedCount,
                    hasRedeemed != null && hasRedeemed,
                    reward.getExpiredAt(),
                    reward.getAddOnService() == null ? null : reward.getAddOnService().getId(),
                    reward.isActive(),
                    reward.isActive(),
                    reward.getCreatedAt(),
                    reward.getUpdatedAt());
        }
    }

    public record RedemptionResponse(
            Long id,
            @JsonProperty("_id") String uid,
            RewardResponse rewardId,
            String code,
            Integer pointsUsed,
            String status,
            LocalDateTime redeemedAt,
            LocalDateTime expiresAt) {
        public static RedemptionResponse from(RewardRedemption redemption) {
            return new RedemptionResponse(
                    redemption.getId(),
                    String.valueOf(redemption.getId()),
                    RewardResponse.from(redemption.getReward()),
                    redemption.getCode(),
                    redemption.getPointsUsed(),
                    redemption.getStatus().name().toLowerCase(),
                    redemption.getRedeemedAt(),
                    redemption.getExpiresAt());
        }
    }

    public record RedemptionEnvelope(
            Long id,
            @JsonProperty("_id") String uid,
            RewardResponse rewardId,
            String code,
            Integer pointsUsed,
            String status,
            LocalDateTime redeemedAt,
            LocalDateTime expiresAt,
            RedemptionResponse redemption) {
        public static RedemptionEnvelope from(RedemptionResponse redemption) {
            return new RedemptionEnvelope(
                    redemption.id(),
                    redemption.uid(),
                    redemption.rewardId(),
                    redemption.code(),
                    redemption.pointsUsed(),
                    redemption.status(),
                    redemption.redeemedAt(),
                    redemption.expiresAt(),
                    redemption);
        }
    }
}
