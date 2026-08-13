package com.shinecraft.server.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class VehicleCatalogDtos {
    private VehicleCatalogDtos() {}

    public record ModelResponse(
            Long id,
            @JsonProperty("_id") String uid,
            String name,
            boolean isNew) {
        public static ModelResponse from(VehicleModel model) {
            return new ModelResponse(
                    model.getId(),
                    String.valueOf(model.getId()),
                    model.getName(),
                    model.isNew());
        }
    }

    public record BrandResponse(
            Long id,
            @JsonProperty("_id") String uid,
            String name,
            List<ModelResponse> models) {
        public static BrandResponse from(VehicleBrand brand) {
            List<ModelResponse> activeModels = brand.getModels() == null
                    ? List.of()
                    : brand.getModels().stream()
                            .filter(VehicleModel::isActive)
                            .map(ModelResponse::from)
                            .toList();
            return new BrandResponse(
                    brand.getId(),
                    String.valueOf(brand.getId()),
                    brand.getName(),
                    activeModels);
        }
    }

    public record BrandRequest(
            @NotBlank @Size(max = 80) String name,
            Boolean active,
            Boolean isActive) {
        public Boolean resolvedActive() {
            return isActive != null ? isActive : active;
        }
    }

    public record ModelRequest(
            @NotBlank @Size(max = 80) String name,
            Boolean isNew,
            Boolean active,
            Boolean isActive) {
        public Boolean resolvedActive() {
            return isActive != null ? isActive : active;
        }
    }
}
