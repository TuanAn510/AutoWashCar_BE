package com.shinecraft.server.promotion;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PromotionDtos {
    private PromotionDtos() {}

    public record PromotionRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description,
            DiscountType discountType,
            String type,
            @DecimalMin("0.0") BigDecimal discountValue,
            Long targetTierId,
            Long membershipTierId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Integer usageLimit,
            Boolean active,
            Boolean isActive) {
        public DiscountType resolvedDiscountType() {
            if (discountType != null) {
                return discountType;
            }
            if ("percentage".equals(type)) {
                return DiscountType.PERCENTAGE;
            }
            return DiscountType.FIXED_AMOUNT;
        }

        public Long resolvedTargetTierId() {
            return membershipTierId != null ? membershipTierId : targetTierId;
        }

        public LocalDateTime resolvedStartAt() {
            return startDate != null ? startDate : startAt;
        }

        public LocalDateTime resolvedEndAt() {
            return endDate != null ? endDate : endAt;
        }

        public Boolean resolvedActive() {
            return isActive != null ? isActive : active;
        }
    }

    public record PromotionResponse(
            Long id,
            @JsonProperty("_id") String uid,
            String code,
            String title,
            String description,
            DiscountType discountType,
            String type,
            BigDecimal discountValue,
            Long targetTierId,
            Long membershipTierId,
            String targetType,
            String targetTierName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Integer usageLimit,
            Integer usedCount,
            boolean active,
            boolean isActive) {
        public static PromotionResponse from(Promotion promotion) {
            return new PromotionResponse(
                    promotion.getId(),
                    String.valueOf(promotion.getId()),
                    promotion.getCode(),
                    promotion.getTitle(),
                    promotion.getDescription(),
                    promotion.getDiscountType(),
                    promotion.getDiscountType() == DiscountType.PERCENTAGE ? "percentage" : "fixed_amount",
                    promotion.getDiscountValue(),
                    promotion.getTargetTier() == null ? null : promotion.getTargetTier().getId(),
                    promotion.getTargetTier() == null ? null : promotion.getTargetTier().getId(),
                    promotion.getTargetTier() == null ? "all" : "membership_tier",
                    promotion.getTargetTier() == null ? null : promotion.getTargetTier().getName(),
                    promotion.getStartAt(),
                    promotion.getEndAt(),
                    promotion.getStartAt(),
                    promotion.getEndAt(),
                    promotion.getUsageLimit(),
                    promotion.getUsedCount(),
                    promotion.isActive(),
                    promotion.isActive());
        }
    }
}
