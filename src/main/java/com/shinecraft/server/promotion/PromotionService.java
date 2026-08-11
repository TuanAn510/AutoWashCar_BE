package com.shinecraft.server.promotion;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyService;
import com.shinecraft.server.loyalty.MembershipTier;
import com.shinecraft.server.loyalty.MembershipTierRepository;
import com.shinecraft.server.user.AuthService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;
    private final MembershipTierRepository tierRepository;
    private final LoyaltyService loyaltyService;
    private final AuthService authService;

    public PromotionService(
            PromotionRepository promotionRepository,
            MembershipTierRepository tierRepository,
            LoyaltyService loyaltyService,
            AuthService authService) {
        this.promotionRepository = promotionRepository;
        this.tierRepository = tierRepository;
        this.loyaltyService = loyaltyService;
        this.authService = authService;
    }

    public List<PromotionDtos.PromotionResponse> activeForCurrentCustomer() {
        LoyaltyAccount account = loyaltyService.getOrCreateAccount(authService.currentUser());
        MembershipTier customerTier = account.getMembershipTier();
        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.findByIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(now, now).stream()
                .filter(promotion -> promotion.getUsageLimit() == null
                        || promotion.getUsedCount() < promotion.getUsageLimit())
                .filter(promotion -> promotion.getTargetTier() == null
                        || (customerTier != null && promotion.getTargetTier().getId().equals(customerTier.getId())))
                .map(PromotionDtos.PromotionResponse::from)
                .toList();
    }

    public List<PromotionDtos.PromotionResponse> all() {
        return promotionRepository.findAll().stream().map(PromotionDtos.PromotionResponse::from).toList();
    }

    @Transactional
    public PromotionDtos.PromotionResponse save(Long id, PromotionDtos.PromotionRequest request) {
        if (request.startAt().isAfter(request.endAt()) || request.startAt().isEqual(request.endAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
        Promotion promotion = id == null
                ? new Promotion()
                : promotionRepository
                        .findById(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Promotion not found"));
        promotion.setCode(request.code().trim().toUpperCase());
        promotion.setTitle(request.title().trim());
        promotion.setDescription(request.description());
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue());
        promotion.setStartAt(request.startAt());
        promotion.setEndAt(request.endAt());
        promotion.setUsageLimit(request.usageLimit());
        if (request.targetTierId() != null) {
            promotion.setTargetTier(tierRepository
                    .findById(request.targetTierId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target membership tier not found")));
        } else {
            promotion.setTargetTier(null);
        }
        if (request.active() != null) {
            promotion.setActive(request.active());
        }
        return PromotionDtos.PromotionResponse.from(promotionRepository.save(promotion));
    }

    @Transactional
    public Promotion claimUsable(Long promotionId, LoyaltyAccount account) {
        Promotion promotion = promotionRepository
                .findById(promotionId)
                .filter(Promotion::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Promotion is not available"));
        LocalDateTime now = LocalDateTime.now();
        if (promotion.getStartAt().isAfter(now) || promotion.getEndAt().isBefore(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion is not within its active period");
        }
        if (promotion.getUsageLimit() != null && promotion.getUsedCount() >= promotion.getUsageLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion usage limit has been reached");
        }
        if (promotion.getTargetTier() != null) {
            MembershipTier tier = account.getMembershipTier();
            if (tier == null || !promotion.getTargetTier().getId().equals(tier.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Promotion does not apply to the current membership tier");
            }
        }
        promotion.setUsedCount(promotion.getUsedCount() + 1);
        return promotion;
    }

    public void restoreUsage(Promotion promotion) {
        if (promotion.getUsedCount() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion usage cannot be restored");
        }
        promotion.setUsedCount(promotion.getUsedCount() - 1);
    }
}
