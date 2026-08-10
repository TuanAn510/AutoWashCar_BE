package com.shinecraft.server.loyalty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class LoyaltyDtos {
    private LoyaltyDtos() {}

    public record TierRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull @Min(0) Integer minPoints,
            @NotNull @DecimalMin("0.0") BigDecimal discountPercent,
            @NotNull @Min(1) Integer bookingWindowDays,
            @NotNull @Min(0) Integer priorityLevel,
            @Size(max = 1000) String description,
            Boolean active) {}

    public record TierResponse(
            Long id,
            String name,
            Integer minPoints,
            BigDecimal discountPercent,
            Integer bookingWindowDays,
            Integer priorityLevel,
            String description,
            boolean active) {
        public static TierResponse from(MembershipTier tier) {
            return new TierResponse(
                    tier.getId(),
                    tier.getName(),
                    tier.getMinPoints(),
                    tier.getDiscountPercent(),
                    tier.getBookingWindowDays(),
                    tier.getPriorityLevel(),
                    tier.getDescription(),
                    tier.isActive());
        }
    }

    public record LoyaltyAccountResponse(
            Integer currentPoints,
            Integer lifetimePoints,
            BigDecimal totalSpending,
            Integer visitCount,
            TierResponse tier) {
        public static LoyaltyAccountResponse from(LoyaltyAccount account) {
            return new LoyaltyAccountResponse(
                    account.getCurrentPoints(),
                    account.getLifetimePoints(),
                    account.getTotalSpending(),
                    account.getVisitCount(),
                    account.getMembershipTier() == null ? null : TierResponse.from(account.getMembershipTier()));
        }
    }

    public record TransactionResponse(
            Long id, LoyaltyTransactionType type, Integer points, String description, LocalDateTime expiresAt, LocalDateTime createdAt) {
        public static TransactionResponse from(LoyaltyTransaction transaction) {
            return new TransactionResponse(
                    transaction.getId(),
                    transaction.getType(),
                    transaction.getPoints(),
                    transaction.getDescription(),
                    transaction.getExpiresAt(),
                    transaction.getCreatedAt());
        }
    }

    public record RewardRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull @Min(1) Integer requiredPoints,
            @NotNull RewardType rewardType,
            @DecimalMin("0.0") BigDecimal discountAmount,
            Long addOnServiceId,
            Boolean active) {}

    public record RewardResponse(
            Long id,
            String name,
            String description,
            Integer requiredPoints,
            RewardType rewardType,
            BigDecimal discountAmount,
            Long addOnServiceId,
            boolean active) {
        public static RewardResponse from(Reward reward) {
            return new RewardResponse(
                    reward.getId(),
                    reward.getName(),
                    reward.getDescription(),
                    reward.getRequiredPoints(),
                    reward.getRewardType(),
                    reward.getDiscountAmount(),
                    reward.getAddOnService() == null ? null : reward.getAddOnService().getId(),
                    reward.isActive());
        }
    }

    public record RedemptionResponse(
            Long id,
            Long rewardId,
            String rewardName,
            String code,
            Integer pointsUsed,
            RewardRedemptionStatus status,
            LocalDateTime redeemedAt,
            LocalDateTime expiresAt) {
        public static RedemptionResponse from(RewardRedemption redemption) {
            return new RedemptionResponse(
                    redemption.getId(),
                    redemption.getReward().getId(),
                    redemption.getReward().getName(),
                    redemption.getCode(),
                    redemption.getPointsUsed(),
                    redemption.getStatus(),
                    redemption.getRedeemedAt(),
                    redemption.getExpiresAt());
        }
    }
}
