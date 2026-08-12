package com.shinecraft.server.promotion;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyService;
import com.shinecraft.server.loyalty.MembershipTier;
import com.shinecraft.server.loyalty.MembershipTierRepository;
import com.shinecraft.server.user.AuthService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<PromotionDtos.PromotionResponse> all() {
        return promotionRepository.findAll().stream().map(PromotionDtos.PromotionResponse::from).toList();
    }

    @Transactional
    public PromotionDtos.PromotionResponse save(Long id, PromotionDtos.PromotionRequest request) {
        LocalDateTime startAt = request.resolvedStartAt();
        LocalDateTime endAt = request.resolvedEndAt();
        if (startAt == null || endAt == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion start date and end date are required");
        }
        if (startAt.isAfter(endAt) || startAt.isEqual(endAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
        Promotion promotion = id == null
                ? new Promotion()
                : promotionRepository
                        .findById(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Promotion not found"));
        String code = request.code().trim().toUpperCase();
        promotionRepository
                .findByCodeIgnoreCase(code)
                .filter(existing -> !Objects.equals(existing.getId(), promotion.getId()))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Promotion code already exists");
                });
        DiscountType discountType = request.resolvedDiscountType();
        BigDecimal discountValue = request.discountValue();
        validateDiscount(discountType, discountValue);
        validateUsageLimit(request.usageLimit(), promotion.getUsedCount());

        promotion.setCode(code);
        promotion.setTitle(request.title().trim());
        promotion.setDescription(request.description());
        promotion.setDiscountType(discountType);
        promotion.setDiscountValue(discountValue);
        promotion.setStartAt(startAt);
        promotion.setEndAt(endAt);
        promotion.setUsageLimit(request.usageLimit());
        if (request.resolvedTargetTierId() != null) {
            promotion.setTargetTier(tierRepository
                    .findById(request.resolvedTargetTierId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target membership tier not found")));
        } else {
            promotion.setTargetTier(null);
        }
        if (request.resolvedActive() != null) {
            promotion.setActive(request.resolvedActive());
        }
        return PromotionDtos.PromotionResponse.from(promotionRepository.save(promotion));
    }

    private void validateDiscount(DiscountType discountType, BigDecimal discountValue) {
        if (discountValue == null || discountValue.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion discount value must be greater than zero");
        }
        if (discountType == DiscountType.PERCENTAGE && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Percentage discount value must not exceed 100");
        }
    }

    private void validateUsageLimit(Integer usageLimit, Integer usedCount) {
        if (usageLimit != null && usageLimit <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion usage limit must be greater than zero");
        }
        if (usageLimit != null && usageLimit < usedCount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion usage limit cannot be lower than used count");
        }
    }

    @Transactional
    public PromotionDtos.PromotionResponse updateStatus(Long id, boolean active) {
        Promotion promotion = promotionRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Promotion not found"));
        promotion.setActive(active);
        return PromotionDtos.PromotionResponse.from(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionDtos.PromotionResponse delete(Long id) {
        Promotion promotion = promotionRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Promotion not found"));
        promotion.setActive(false);
        return PromotionDtos.PromotionResponse.from(promotionRepository.save(promotion));
    }

    @Transactional
    public Promotion claimUsable(Long promotionId, LoyaltyAccount account) {
        Promotion promotion = promotionRepository
                .findByIdForUpdate(promotionId)
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
