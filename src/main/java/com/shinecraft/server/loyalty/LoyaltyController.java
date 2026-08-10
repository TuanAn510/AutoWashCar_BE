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
        return ApiResponse.ok("Lay loyalty thanh cong", loyaltyService.myAccount());
    }

    @GetMapping("/api/loyalty/me/transactions")
    ApiResponse<List<LoyaltyDtos.TransactionResponse>> myTransactions() {
        return ApiResponse.ok("Lay giao dich diem thanh cong", loyaltyService.myTransactions());
    }

    @GetMapping("/api/loyalty/tiers")
    ApiResponse<List<LoyaltyDtos.TierResponse>> tiers() {
        return ApiResponse.ok("Lay hang thanh vien thanh cong", loyaltyService.tiers());
    }

    @GetMapping("/api/rewards")
    ApiResponse<List<LoyaltyDtos.RewardResponse>> rewards() {
        return ApiResponse.ok("Lay reward thanh cong", loyaltyService.rewards());
    }

    @PostMapping("/api/rewards/{rewardId}/redeem")
    ApiResponse<LoyaltyDtos.RedemptionResponse> redeem(@PathVariable Long rewardId) {
        return ApiResponse.ok("Doi reward thanh cong", loyaltyService.redeem(rewardId));
    }

    @GetMapping("/api/rewards/my-redemptions")
    ApiResponse<List<LoyaltyDtos.RedemptionResponse>> redemptions() {
        return ApiResponse.ok("Lay reward da doi thanh cong", loyaltyService.myRedemptions());
    }

    @PostMapping("/api/admin/loyalty/tiers")
    ApiResponse<LoyaltyDtos.TierResponse> createTier(@Valid @RequestBody LoyaltyDtos.TierRequest request) {
        return ApiResponse.ok("Tao hang thanh vien thanh cong", loyaltyService.saveTier(null, request));
    }

    @PutMapping("/api/admin/loyalty/tiers/{id}")
    ApiResponse<LoyaltyDtos.TierResponse> updateTier(
            @PathVariable Long id, @Valid @RequestBody LoyaltyDtos.TierRequest request) {
        return ApiResponse.ok("Cap nhat hang thanh vien thanh cong", loyaltyService.saveTier(id, request));
    }

    @PostMapping("/api/admin/rewards")
    ApiResponse<LoyaltyDtos.RewardResponse> createReward(@Valid @RequestBody LoyaltyDtos.RewardRequest request) {
        return ApiResponse.ok("Tao reward thanh cong", loyaltyService.saveReward(null, request));
    }

    @PutMapping("/api/admin/rewards/{id}")
    ApiResponse<LoyaltyDtos.RewardResponse> updateReward(
            @PathVariable Long id, @Valid @RequestBody LoyaltyDtos.RewardRequest request) {
        return ApiResponse.ok("Cap nhat reward thanh cong", loyaltyService.saveReward(id, request));
    }
}
