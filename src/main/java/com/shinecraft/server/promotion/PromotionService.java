package com.shinecraft.server.promotion;

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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;
    private final AuditLogRepository auditLogRepository;
    private final MembershipTierRepository tierRepository;
    private final LoyaltyService loyaltyService;
    private final AuthService authService;
    private final NotificationService notificationService;

    public PromotionService(
            PromotionRepository promotionRepository,
            AuditLogRepository auditLogRepository,
            MembershipTierRepository tierRepository,
            LoyaltyService loyaltyService,
            AuthService authService,
            NotificationService notificationService) {
        this.promotionRepository = promotionRepository;
        this.auditLogRepository = auditLogRepository;
        this.tierRepository = tierRepository;
        this.loyaltyService = loyaltyService;
        this.authService = authService;
        this.notificationService = notificationService;
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
        Promotion saved = promotionRepository.save(promotion);
        // Notify admins about new/updated promotion
        boolean isNew = id == null;
        String action = isNew ? "Tạo mới" : "Cập nhật";
        String discountDesc = saved.getDiscountType() == DiscountType.PERCENTAGE
                ? saved.getDiscountValue() + "%"
                : String.format("%,.0fđ", saved.getDiscountValue());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String adminMsg = action + " khuyến mãi \"" + saved.getTitle() + "\" - Giảm " + discountDesc
                + " từ " + saved.getStartAt().format(fmt) + " đến " + saved.getEndAt().format(fmt) + ".";
        notificationService.notifyAdmins("PROMOTION", "Khuyến mãi đã " + action.toLowerCase(), adminMsg, "PROMOTION", saved.getId());
        // Notify all active customers about new promotion
        if (isNew && Boolean.TRUE.equals(saved.isActive())) {
            String customerMsg = "Khuyến mãi mới \"" + saved.getTitle() + "\" - Giảm " + discountDesc
                    + " từ " + saved.getStartAt().format(fmt) + " đến " + saved.getEndAt().format(fmt)
                    + ". nhanh tay kẻo lỡ!";
            notificationService.notifyCustomers("PROMOTION", "Khuyến mãi mới!", customerMsg, "PROMOTION", saved.getId());
        }
        return PromotionDtos.PromotionResponse.from(saved);
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

    public void recordPromotionUsed(Promotion promotion, User customer, Long bookingId) {
        int usedCountAfter = promotion.getUsedCount();
        recordPromotionAudit(
                "PROMOTION_USED", promotion, customer, bookingId, usedCountAfter - 1, usedCountAfter);
    }

    public void recordPromotionRestored(Promotion promotion, User customer, Long bookingId, int usedCountBefore) {
        recordPromotionAudit(
                "PROMOTION_RESTORED", promotion, customer, bookingId, usedCountBefore, promotion.getUsedCount());
    }

    private void recordPromotionAudit(
            String action, Promotion promotion, User customer, Long bookingId, int usedCountBefore, int usedCountAfter) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActor(customer);
        auditLog.setTargetUser(customer);
        auditLog.setAction(action);
        auditLog.setBeforeValue(auditValue(promotion, bookingId, usedCountBefore));
        auditLog.setAfterValue(auditValue(promotion, bookingId, usedCountAfter));
        auditLogRepository.save(auditLog);
    }

    private String auditValue(Promotion promotion, Long bookingId, int usedCount) {
        return "{\"promotionId\":%d,\"promotionCode\":\"%s\",\"bookingId\":%d,\"usedCount\":%d}"
                .formatted(promotion.getId(), escapeJson(promotion.getCode()), bookingId, usedCount);
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u%04x".formatted((int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
