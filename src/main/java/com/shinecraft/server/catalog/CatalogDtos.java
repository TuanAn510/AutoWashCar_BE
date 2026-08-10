package com.shinecraft.server.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            Boolean active) {}

    public record ServiceRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @NotNull @Min(1) Integer durationMinutes,
            Boolean active) {}

    public record CategoryResponse(Long id, String name, String description, boolean active) {
        public static CategoryResponse from(ServiceCategory category) {
            return new CategoryResponse(
                    category.getId(), category.getName(), category.getDescription(), category.isActive());
        }
    }

    public record ServiceResponse(
            Long id,
            Long categoryId,
            String categoryName,
            String name,
            String description,
            BigDecimal price,
            Integer durationMinutes,
            boolean active) {
        public static ServiceResponse from(CarWashService service) {
            return new ServiceResponse(
                    service.getId(),
                    service.getCategory().getId(),
                    service.getCategory().getName(),
                    service.getName(),
                    service.getDescription(),
                    service.getPrice(),
                    service.getDurationMinutes(),
                    service.isActive());
        }
    }
}
