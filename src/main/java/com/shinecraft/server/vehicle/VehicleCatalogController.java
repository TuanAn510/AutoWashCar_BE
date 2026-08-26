package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.ApiListResponse;
import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vehicle Catalog")
@RestController
public class VehicleCatalogController {
    private final VehicleCatalogService catalogService;

    public VehicleCatalogController(VehicleCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/vehicle-brands")
    ApiListResponse<VehicleCatalogDtos.BrandResponse> activeBrands() {
        return ApiListResponse.ok("Vehicle brands retrieved successfully", catalogService.activeBrands());
    }

    @GetMapping("/api/vehicle-brands/{brandId}/models")
    ApiListResponse<VehicleCatalogDtos.ModelResponse> activeModels(@PathVariable Long brandId) {
        return ApiListResponse.ok("Vehicle models retrieved successfully", catalogService.activeModels(brandId));
    }

    @GetMapping("/api/admin/vehicle-brands")
    ApiListResponse<VehicleCatalogDtos.BrandResponse> allBrands() {
        return ApiListResponse.ok("Vehicle brands retrieved successfully", catalogService.allBrands());
    }

    @PostMapping("/api/admin/vehicle-brands")
    ApiResponse<VehicleCatalogDtos.BrandResponse> createBrand(
            @Valid @RequestBody VehicleCatalogDtos.BrandRequest request) {
        return ApiResponse.ok("Vehicle brand created successfully", catalogService.createBrand(request));
    }

    @PostMapping("/api/admin/vehicle-brands/{brandId}/models")
    ApiResponse<VehicleCatalogDtos.ModelResponse> createModel(
            @PathVariable Long brandId, @Valid @RequestBody VehicleCatalogDtos.ModelRequest request) {
        return ApiResponse.ok("Vehicle model created successfully", catalogService.createModel(brandId, request));
    }
}
