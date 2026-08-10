package com.shinecraft.server.promotion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PromotionDtos {
    private PromotionDtos() {}

    public record PromotionRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description,
            @NotNull DiscountType discountType,
            @NotNull @DecimalMin("0.0") BigDecimal discountValue,
            Long targetTierId,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            Integer usageLimit,
            Boolean active) {}

    public record PromotionResponse(
            Long id,
            String code,
            String title,
            String description,
            DiscountType discountType,
            BigDecimal discountValue,
            Long targetTierId,
            String targetTierName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer usageLimit,
            Integer usedCount,
            boolean active) {
        public static PromotionResponse from(Promotion promotion) {
            return new PromotionResponse(
                    promotion.getId(),
                    promotion.getCode(),
                    promotion.getTitle(),
                    promotion.getDescription(),
                    promotion.getDiscountType(),
                    promotion.getDiscountValue(),
                    promotion.getTargetTier() == null ? null : promotion.getTargetTier().getId(),
                    promotion.getTargetTier() == null ? null : promotion.getTargetTier().getName(),
                    promotion.getStartAt(),
                    promotion.getEndAt(),
                    promotion.getUsageLimit(),
                    promotion.getUsedCount(),
                    promotion.isActive());
        }
    }
}
