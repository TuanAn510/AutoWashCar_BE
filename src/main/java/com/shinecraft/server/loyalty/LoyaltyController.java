package com.shinecraft.server.loyalty;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/api/loyalty/tiers")
    ApiResponse<List<LoyaltyDtos.TierResponse>> tiers() {
        return ApiResponse.ok("Membership tiers retrieved successfully", loyaltyService.tiers());
    }

    @GetMapping("/api/rewards")
    ApiResponse<List<LoyaltyDtos.RewardResponse>> rewards() {
        return ApiResponse.ok("Rewards retrieved successfully", loyaltyService.rewards());
    }

    @PostMapping("/api/rewards/{rewardId}/redeem")
    ApiResponse<LoyaltyDtos.RedemptionResponse> redeem(@PathVariable Long rewardId) {
        return ApiResponse.ok("Reward redeemed successfully", loyaltyService.redeem(rewardId));
    }

    @GetMapping("/api/rewards/my-redemptions")
    ApiResponse<List<LoyaltyDtos.RedemptionResponse>> redemptions() {
        return ApiResponse.ok("Reward redemptions retrieved successfully", loyaltyService.myRedemptions());
    }

    @PostMapping("/api/admin/loyalty/tiers")
    ApiResponse<LoyaltyDtos.TierResponse> createTier(@Valid @RequestBody LoyaltyDtos.TierRequest request) {
        return ApiResponse.ok("Membership tier created successfully", loyaltyService.saveTier(null, request));
    }

    @PutMapping("/api/admin/loyalty/tiers/{id}")
    ApiResponse<LoyaltyDtos.TierResponse> updateTier(
            @PathVariable Long id, @Valid @RequestBody LoyaltyDtos.TierRequest request) {
        return ApiResponse.ok("Membership tier updated successfully", loyaltyService.saveTier(id, request));
    }

    @PostMapping("/api/admin/rewards")
    ApiResponse<LoyaltyDtos.RewardResponse> createReward(@Valid @RequestBody LoyaltyDtos.RewardRequest request) {
        return ApiResponse.ok("Reward created successfully", loyaltyService.saveReward(null, request));
    }

    @PutMapping("/api/admin/rewards/{id}")
    ApiResponse<LoyaltyDtos.RewardResponse> updateReward(
            @PathVariable Long id, @Valid @RequestBody LoyaltyDtos.RewardRequest request) {
        return ApiResponse.ok("Reward updated successfully", loyaltyService.saveReward(id, request));
    }
}
