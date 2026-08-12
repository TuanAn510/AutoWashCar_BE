package com.shinecraft.server.promotion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.loyalty.LoyaltyService;
import com.shinecraft.server.loyalty.MembershipTierRepository;
import com.shinecraft.server.user.AuthService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromotionServiceTest {
    private PromotionRepository promotionRepository;
    private PromotionService promotionService;

    @BeforeEach
    void setUp() {
        promotionRepository = mock(PromotionRepository.class);
        promotionService = new PromotionService(
                promotionRepository,
                mock(MembershipTierRepository.class),
                mock(LoyaltyService.class),
                mock(AuthService.class));
    }

    @Test
    void rejectsPercentageAboveOneHundred() {
        assertThatThrownBy(() -> promotionService.save(null, request(DiscountType.PERCENTAGE, "100.01", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Percentage discount value must not exceed 100");
    }

    @Test
    void rejectsNonPositiveDiscountValues() {
        assertThatThrownBy(() -> promotionService.save(null, request(DiscountType.FIXED_AMOUNT, "0", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Promotion discount value must be greater than zero");

        assertThatThrownBy(() -> promotionService.save(null, request(DiscountType.PERCENTAGE, "-1", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Promotion discount value must be greater than zero");
    }

    @Test
    void rejectsNonPositiveUsageLimits() {
        assertThatThrownBy(() -> promotionService.save(null, request(DiscountType.FIXED_AMOUNT, "10", 0)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Promotion usage limit must be greater than zero");
    }

    @Test
    void rejectsUsageLimitBelowCurrentUsedCountOnUpdate() {
        Promotion promotion = promotionWithId(1L, "SAVE10", 3);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(promotion));
        when(promotionRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(promotion));

        assertThatThrownBy(() -> promotionService.save(1L, request(DiscountType.FIXED_AMOUNT, "10", 2)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Promotion usage limit cannot be lower than used count");
    }

    @Test
    void rejectsDuplicateCodeWithControlledConflict() {
        Promotion existing = promotionWithId(2L, "SAVE10", 0);
        when(promotionRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> promotionService.save(null, request(DiscountType.FIXED_AMOUNT, "10", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Promotion code already exists");
    }

    @Test
    void allowsUpdatingPromotionWithItsOwnCode() {
        Promotion promotion = promotionWithId(1L, "SAVE10", 1);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(promotion));
        when(promotionRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(promotion));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        promotionService.save(1L, request(DiscountType.PERCENTAGE, "10", 1));
    }

    @Test
    void acceptsOneHundredPercentAndUnlimitedUsage() {
        when(promotionRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.empty());
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        promotionService.save(null, request(DiscountType.PERCENTAGE, "100", null));
    }

    @Test
    void rejectsEqualStartAndEndDates() {
        LocalDateTime date = LocalDateTime.of(2026, 1, 1, 8, 0);
        PromotionDtos.PromotionRequest request = new PromotionDtos.PromotionRequest(
                "SAVE10",
                "Save ten",
                null,
                DiscountType.FIXED_AMOUNT,
                null,
                BigDecimal.TEN,
                null,
                null,
                date,
                date,
                null,
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> promotionService.save(null, request))
                .isInstanceOf(ApiException.class)
                .hasMessage("End date must be after start date");
    }

    @Test
    void restoreUsageRejectsZeroAndLeavesItUnchanged() {
        Promotion promotion = promotionWithId(1L, "SAVE10", 0);

        assertThatThrownBy(() -> promotionService.restoreUsage(promotion))
                .isInstanceOf(ApiException.class)
                .hasMessage("Promotion usage cannot be restored");

        assertThat(promotion.getUsedCount()).isZero();
    }

    private PromotionDtos.PromotionRequest request(DiscountType type, String value, Integer usageLimit) {
        LocalDateTime startAt = LocalDateTime.of(2026, 1, 1, 8, 0);
        return new PromotionDtos.PromotionRequest(
                " save10 ",
                "Save ten",
                null,
                type,
                null,
                new BigDecimal(value),
                null,
                null,
                startAt,
                startAt.plusDays(1),
                null,
                null,
                usageLimit,
                null,
                null);
    }

    private Promotion promotionWithId(Long id, String code, int usedCount) {
        Promotion promotion = new Promotion();
        promotion.setId(id);
        promotion.setCode(code);
        promotion.setTitle("Save ten");
        promotion.setDiscountType(DiscountType.FIXED_AMOUNT);
        promotion.setDiscountValue(BigDecimal.TEN);
        promotion.setStartAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        promotion.setEndAt(LocalDateTime.of(2026, 1, 2, 8, 0));
        promotion.setUsedCount(usedCount);
        return promotion;
    }
}
