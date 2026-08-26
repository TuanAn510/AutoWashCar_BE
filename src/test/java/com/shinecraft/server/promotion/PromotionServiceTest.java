package com.shinecraft.server.promotion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinecraft.server.audit.AuditLog;
import com.shinecraft.server.audit.AuditLogRepository;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyService;
import com.shinecraft.server.loyalty.MembershipTier;
import com.shinecraft.server.loyalty.MembershipTierRepository;
import com.shinecraft.server.notification.NotificationService;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromotionServiceTest {
    private PromotionRepository promotionRepository;
    private AuditLogRepository auditLogRepository;
    private MembershipTierRepository tierRepository;
    private LoyaltyService loyaltyService;
    private AuthService authService;
    private NotificationService notificationService;
    private PromotionService promotionService;

    @BeforeEach
    void setUp() {
        promotionRepository = mock(PromotionRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        tierRepository = mock(MembershipTierRepository.class);
        loyaltyService = mock(LoyaltyService.class);
        authService = mock(AuthService.class);
        notificationService = mock(NotificationService.class);
        promotionService = new PromotionService(
                promotionRepository,
                auditLogRepository,
                tierRepository,
                loyaltyService,
                authService,
                notificationService);
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

    @Test
    void recordsPromotionUseAuditWithEscapedDeterministicJson() {
        Promotion promotion = promotionWithId(12L, "SAVE\"\\\n", 1);
        User customer = new User();
        customer.setId(7L);

        promotionService.recordPromotionUsed(promotion, customer, 34L);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActor()).isSameAs(customer);
        assertThat(audit.getTargetUser()).isSameAs(customer);
        assertThat(audit.getAction()).isEqualTo("PROMOTION_USED");
        assertThat(audit.getBeforeValue())
                .isEqualTo("{\"promotionId\":12,\"promotionCode\":\"SAVE\\\"\\\\\\n\",\"bookingId\":34,\"usedCount\":0}");
        assertThat(audit.getAfterValue())
                .isEqualTo("{\"promotionId\":12,\"promotionCode\":\"SAVE\\\"\\\\\\n\",\"bookingId\":34,\"usedCount\":1}");
    }

    @Test
    void globalPromotionCanBeClaimedByCustomerWithoutMembershipTier() {
        Promotion promotion = activePromotion(1L, "GLOBAL", 0);
        LoyaltyAccount account = new LoyaltyAccount();
        when(promotionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(promotion));

        promotionService.claimUsable(1L, account);

        assertThat(promotion.getUsedCount()).isEqualTo(1);
    }

    @Test
    void exactTargetTierCustomerCanClaimPromotion() {
        MembershipTier targetTier = tier(10L, "Silver");
        Promotion promotion = activePromotion(1L, "SILVER", 0);
        promotion.setTargetTier(targetTier);
        LoyaltyAccount account = new LoyaltyAccount();
        account.setMembershipTier(targetTier);
        when(promotionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(promotion));

        promotionService.claimUsable(1L, account);

        assertThat(promotion.getUsedCount()).isEqualTo(1);
    }

    @Test
    void targetTierMismatchAndNoTierCustomerAreRejected() {
        MembershipTier targetTier = tier(10L, "Silver");
        Promotion promotion = activePromotion(1L, "SILVER", 0);
        promotion.setTargetTier(targetTier);
        when(promotionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(promotion));

        LoyaltyAccount mismatchingAccount = new LoyaltyAccount();
        mismatchingAccount.setMembershipTier(tier(20L, "Gold"));
        assertThatThrownBy(() -> promotionService.claimUsable(1L, mismatchingAccount))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception).hasMessage("Promotion does not apply to the current membership tier");
                });

        assertThatThrownBy(() -> promotionService.claimUsable(1L, new LoyaltyAccount()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception).hasMessage("Promotion does not apply to the current membership tier");
                });
        assertThat(promotion.getUsedCount()).isZero();
    }

    @Test
    void createAndUpdateResolveTheExactTargetTier() {
        MembershipTier silver = tier(10L, "Silver");
        MembershipTier gold = tier(20L, "Gold");
        Promotion existing = promotionWithId(1L, "UPDATE", 0);
        when(tierRepository.findById(10L)).thenReturn(Optional.of(silver));
        when(tierRepository.findById(20L)).thenReturn(Optional.of(gold));
        when(promotionRepository.findByCodeIgnoreCase("CREATE")).thenReturn(Optional.empty());
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(promotionRepository.findByCodeIgnoreCase("UPDATE")).thenReturn(Optional.of(existing));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionDtos.PromotionResponse created = promotionService.save(null, requestWithTargetTier("CREATE", 10L));
        PromotionDtos.PromotionResponse updated = promotionService.save(1L, requestWithTargetTier("UPDATE", 20L));

        assertThat(created.targetTierId()).isEqualTo(10L);
        assertThat(updated.targetTierId()).isEqualTo(20L);
        assertThat(existing.getTargetTier()).isSameAs(gold);
    }

    @Test
    void unknownTargetTierIsRejectedWithNotFound() {
        when(promotionRepository.findByCodeIgnoreCase("CREATE")).thenReturn(Optional.empty());
        when(tierRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.save(null, requestWithTargetTier("CREATE", 99L)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception).hasMessage("Target membership tier not found");
                });
    }

    @Test
    void activePromotionListingUsesExactTargetTierAndKeepsGlobalPromotionVisible() {
        MembershipTier silver = tier(10L, "Silver");
        MembershipTier gold = tier(20L, "Gold");
        Promotion global = activePromotion(1L, "GLOBAL", 0);
        Promotion targeted = activePromotion(2L, "SILVER", 0);
        targeted.setTargetTier(silver);
        LoyaltyAccount account = new LoyaltyAccount();
        when(authService.currentUser()).thenReturn(new User());
        when(loyaltyService.getOrCreateAccount(any())).thenReturn(account);
        when(promotionRepository.findByIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(any(), any()))
                .thenReturn(List.of(global, targeted));

        account.setMembershipTier(silver);
        assertThat(promotionService.activeForCurrentCustomer().stream().map(PromotionDtos.PromotionResponse::code))
                .containsExactly("GLOBAL", "SILVER");

        account.setMembershipTier(gold);
        assertThat(promotionService.activeForCurrentCustomer().stream().map(PromotionDtos.PromotionResponse::code))
                .containsExactly("GLOBAL");

        account.setMembershipTier(null);
        assertThat(promotionService.activeForCurrentCustomer().stream().map(PromotionDtos.PromotionResponse::code))
                .containsExactly("GLOBAL");
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

    private PromotionDtos.PromotionRequest requestWithTargetTier(String code, Long targetTierId) {
        LocalDateTime startAt = LocalDateTime.of(2026, 1, 1, 8, 0);
        return new PromotionDtos.PromotionRequest(
                code,
                code,
                null,
                DiscountType.FIXED_AMOUNT,
                null,
                BigDecimal.TEN,
                targetTierId,
                null,
                startAt,
                startAt.plusDays(1),
                null,
                null,
                null,
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

    private Promotion activePromotion(Long id, String code, int usedCount) {
        Promotion promotion = promotionWithId(id, code, usedCount);
        promotion.setStartAt(LocalDateTime.now().minusMinutes(1));
        promotion.setEndAt(LocalDateTime.now().plusMinutes(1));
        return promotion;
    }

    private MembershipTier tier(Long id, String name) {
        MembershipTier tier = new MembershipTier();
        tier.setId(id);
        tier.setName(name);
        return tier;
    }
}
