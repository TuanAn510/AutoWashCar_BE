package com.shinecraft.server.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            Boolean active,
            Boolean isActive) {
        public Boolean resolvedActive() {
            return isActive != null ? isActive : active;
        }
    }

    public record ServiceRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @Min(1) Integer durationMinutes,
            @Min(1) Integer estimatedDuration,
            Boolean active,
            Boolean isActive) {
        public Integer resolvedDuration() {
            return estimatedDuration != null ? estimatedDuration : durationMinutes;
        }

        public Boolean resolvedActive() {
            return isActive != null ? isActive : active;
        }
    }

    public record CategoryResponse(
            Long id,
            @JsonProperty("_id") String uid,
            String name,
            String description,
            boolean active,
            boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public static CategoryResponse from(ServiceCategory category) {
            return new CategoryResponse(
                    category.getId(),
                    String.valueOf(category.getId()),
                    category.getName(),
                    category.getDescription(),
                    category.isActive(),
                    category.isActive(),
                    category.getCreatedAt(),
                    category.getUpdatedAt());
        }
    }

    public record ServiceResponse(
            Long id,
            @JsonProperty("_id") String uid,
            CategoryResponse categoryId,
            Long legacyCategoryId,
            String categoryName,
            String slug,
            String name,
            String description,
            BigDecimal price,
            Integer durationMinutes,
            Integer estimatedDuration,
            boolean active,
            boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public static ServiceResponse from(CarWashService service) {
            return new ServiceResponse(
                    service.getId(),
                    String.valueOf(service.getId()),
                    CategoryResponse.from(service.getCategory()),
                    service.getCategory().getId(),
                    service.getCategory().getName(),
                    slugify(service.getName()),
                    service.getName(),
                    service.getDescription(),
                    service.getPrice(),
                    service.getDurationMinutes(),
                    service.getDurationMinutes(),
                    service.isActive(),
                    service.isActive(),
                    service.getCreatedAt(),
                    service.getUpdatedAt());
        }

        private static String slugify(String value) {
            return value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        }
    }
}
