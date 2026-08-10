package com.shinecraft.server.promotion;

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

@Tag(name = "Promotions")
@RestController
public class PromotionController {
    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping("/api/promotions/active")
    ApiResponse<List<PromotionDtos.PromotionResponse>> active() {
        return ApiResponse.ok("Available promotions retrieved successfully", promotionService.activeForCurrentCustomer());
    }

    @GetMapping("/api/admin/promotions")
    ApiResponse<List<PromotionDtos.PromotionResponse>> all() {
        return ApiResponse.ok("Admin promotions retrieved successfully", promotionService.all());
    }

    @PostMapping("/api/admin/promotions")
    ApiResponse<PromotionDtos.PromotionResponse> create(@Valid @RequestBody PromotionDtos.PromotionRequest request) {
        return ApiResponse.ok("Promotion created successfully", promotionService.save(null, request));
    }

    @PutMapping("/api/admin/promotions/{id}")
    ApiResponse<PromotionDtos.PromotionResponse> update(
            @PathVariable Long id, @Valid @RequestBody PromotionDtos.PromotionRequest request) {
        return ApiResponse.ok("Promotion updated successfully", promotionService.save(id, request));
    }
}
