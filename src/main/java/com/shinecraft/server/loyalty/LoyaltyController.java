package com.shinecraft.server.loyalty;

import com.shinecraft.server.common.ApiResponse;
import com.shinecraft.server.common.ApiListResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Loyalty")
@RestController
public class LoyaltyController {
    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/api/loyalty/me")
    ApiResponse<LoyaltyDtos.LoyaltyAccountResponse> myAccount() {
        return ApiResponse.ok("Loyalty account retrieved successfully", loyaltyService.myAccount());
    }

    @GetMapping("/api/loyalty/me/transactions")
    ApiResponse<List<LoyaltyDtos.TransactionResponse>> myTransactions() {
        return ApiResponse.ok("Loyalty transactions retrieved successfully", loyaltyService.myTransactions());
    }

    @GetMapping("/api/loyalty/customers")
    @PreAuthorize("hasAnyAuthority('ROLE_STAFF', 'ROLE_ADMIN')")
    ApiListResponse<Map<String, Object>> loyaltyCustomers(@RequestParam(required = false) String search) {
        return ApiListResponse.ok("Loyalty customers retrieved successfully", loyaltyService.customersWithLoyalty(search));
    }

    @GetMapping("/api/loyalty/customers/{customerId}")
    @PreAuthorize("hasAnyAuthority('ROLE_STAFF', 'ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.LoyaltyAccountResponse> customerAccount(@PathVariable Long customerId) {
        return ApiResponse.ok("Customer loyalty account retrieved successfully", loyaltyService.customerAccount(customerId));
    }

    @GetMapping("/api/loyalty/customers/{customerId}/transactions")
    @PreAuthorize("hasAnyAuthority('ROLE_STAFF', 'ROLE_ADMIN')")
    ApiListResponse<LoyaltyDtos.TransactionResponse> customerTransactions(@PathVariable Long customerId) {
        return ApiListResponse.ok(
                "Customer loyalty transactions retrieved successfully",
                loyaltyService.customerTransactions(customerId));
    }

    @GetMapping("/api/loyalty/customers/{customerId}/redemptions")
    @PreAuthorize("hasAnyAuthority('ROLE_STAFF', 'ROLE_ADMIN')")
    ApiListResponse<LoyaltyDtos.RedemptionResponse> customerRedemptions(@PathVariable Long customerId) {
        return ApiListResponse.ok(
                "Customer redemptions retrieved successfully",
                loyaltyService.customerRedemptions(customerId));
    }

    @GetMapping("/api/loyalty/tiers")
    ApiResponse<List<LoyaltyDtos.TierResponse>> tiers() {
        return ApiResponse.ok("Membership tiers retrieved successfully", loyaltyService.tiers());
    }

    @GetMapping("/api/membership-tiers")
    ApiListResponse<LoyaltyDtos.TierResponse> tiersForFrontend() {
        return ApiListResponse.ok("Membership tiers retrieved successfully", loyaltyService.tiers());
    }

    @GetMapping("/api/rewards")
    ApiResponse<List<LoyaltyDtos.RewardResponse>> rewards() {
        return ApiResponse.ok("Rewards retrieved successfully", loyaltyService.rewards());
    }

    @PostMapping("/api/rewards/{rewardId}/redeem")
    ApiResponse<LoyaltyDtos.RedemptionEnvelope> redeem(@PathVariable Long rewardId) {
        return ApiResponse.ok("Reward redeemed successfully", LoyaltyDtos.RedemptionEnvelope.from(loyaltyService.redeem(rewardId)));
    }

    @GetMapping("/api/rewards/my-redemptions")
    ApiResponse<List<LoyaltyDtos.RedemptionResponse>> redemptions() {
        return ApiResponse.ok("Reward redemptions retrieved successfully", loyaltyService.myRedemptions());
    }

    @GetMapping("/api/rewards/me/redemptions")
    ApiListResponse<LoyaltyDtos.RedemptionResponse> redemptionsForFrontend() {
        return ApiListResponse.ok("Reward redemptions retrieved successfully", loyaltyService.myRedemptions());
    }

    @PatchMapping("/api/rewards/redemptions/{redemptionId}/use")
    @PreAuthorize("hasAnyAuthority('ROLE_STAFF', 'ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.RedemptionResponse> markRedemptionUsed(@PathVariable Long redemptionId) {
        return ApiResponse.ok("Reward redemption marked as used successfully", loyaltyService.markRedemptionUsed(redemptionId));
    }

    @PostMapping("/api/admin/loyalty/tiers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.TierResponse> createTier(@Valid @RequestBody LoyaltyDtos.TierRequest request) {
        return ApiResponse.ok("Membership tier created successfully", loyaltyService.saveTier(null, request));
    }

    @PostMapping("/api/membership-tiers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.TierResponse> createTierForFrontend(@Valid @RequestBody LoyaltyDtos.TierRequest request) {
        return createTier(request);
    }

    @PutMapping("/api/admin/loyalty/tiers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.TierResponse> updateTier(
            @PathVariable Long id, @Valid @RequestBody LoyaltyDtos.TierRequest request) {
        return ApiResponse.ok("Membership tier updated successfully", loyaltyService.saveTier(id, request));
    }

    @PatchMapping("/api/membership-tiers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.TierResponse> updateTierForFrontend(
            @PathVariable Long id, @Valid @RequestBody LoyaltyDtos.TierRequest request) {
        return updateTier(id, request);
    }

    @PostMapping("/api/admin/rewards")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.RewardResponse> createReward(@Valid @RequestBody LoyaltyDtos.RewardRequest request) {
        return ApiResponse.ok("Reward created successfully", loyaltyService.saveReward(null, request));
    }

    @PostMapping("/api/rewards")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.RewardResponse> createRewardForFrontend(@Valid @RequestBody LoyaltyDtos.RewardRequest request) {
        return createReward(request);
    }

    @PutMapping("/api/admin/rewards/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.RewardResponse> updateReward(
            @PathVariable Long id, @Valid @RequestBody LoyaltyDtos.RewardRequest request) {
        return ApiResponse.ok("Reward updated successfully", loyaltyService.saveReward(id, request));
    }

    @PatchMapping("/api/rewards/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.RewardResponse> updateRewardForFrontend(
            @PathVariable Long id, @Valid @RequestBody LoyaltyDtos.RewardRequest request) {
        return updateReward(id, request);
    }

    @DeleteMapping("/api/membership-tiers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.TierResponse> deleteTierForFrontend(@PathVariable Long id) {
        return ApiResponse.ok("Membership tier deactivated successfully", loyaltyService.deactivateTier(id));
    }

    @DeleteMapping("/api/rewards/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<LoyaltyDtos.RewardResponse> deleteRewardForFrontend(@PathVariable Long id) {
        return ApiResponse.ok("Reward deactivated successfully", loyaltyService.deactivateReward(id));
    }
}
