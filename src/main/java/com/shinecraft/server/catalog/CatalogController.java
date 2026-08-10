package com.shinecraft.server.catalog;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Catalog")
@RestController
public class CatalogController {
    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/catalog/categories")
    ApiResponse<List<CatalogDtos.CategoryResponse>> categories() {
        return ApiResponse.ok("Lay danh muc thanh cong", catalogService.categories());
    }

    @GetMapping("/api/catalog/services")
    ApiResponse<List<CatalogDtos.ServiceResponse>> services() {
        return ApiResponse.ok("Lay dich vu thanh cong", catalogService.services());
    }

    @PostMapping("/api/admin/catalog/categories")
    ApiResponse<CatalogDtos.CategoryResponse> createCategory(@Valid @RequestBody CatalogDtos.CategoryRequest request) {
        return ApiResponse.ok("Tao danh muc thanh cong", catalogService.createCategory(request));
    }

    @PutMapping("/api/admin/catalog/categories/{id}")
    ApiResponse<CatalogDtos.CategoryResponse> updateCategory(
            @PathVariable Long id, @Valid @RequestBody CatalogDtos.CategoryRequest request) {
        return ApiResponse.ok("Cap nhat danh muc thanh cong", catalogService.updateCategory(id, request));
    }

    @PostMapping("/api/admin/catalog/services")
    ApiResponse<CatalogDtos.ServiceResponse> createService(@Valid @RequestBody CatalogDtos.ServiceRequest request) {
        return ApiResponse.ok("Tao dich vu thanh cong", catalogService.createService(request));
    }

    @PutMapping("/api/admin/catalog/services/{id}")
    ApiResponse<CatalogDtos.ServiceResponse> updateService(
            @PathVariable Long id, @Valid @RequestBody CatalogDtos.ServiceRequest request) {
        return ApiResponse.ok("Cap nhat dich vu thanh cong", catalogService.updateService(id, request));
    }
}
