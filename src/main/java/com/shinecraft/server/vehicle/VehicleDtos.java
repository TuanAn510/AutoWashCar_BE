package com.shinecraft.server.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class VehicleDtos {
    private VehicleDtos() {}

    public record VehicleRequest(
            @Size(max = 20) String licensePlate,
            @Size(max = 80) String brand,
            @Size(max = 80) String model,
            @Size(max = 40) String color,
            Integer manufactureYear,
            Integer year,
            String carType,
            String brandId,
            String modelId,
            @Size(max = 80) String suggestedBrandName,
            @Size(max = 80) String suggestedModelName) {
        public Integer resolvedYear() {
            return year != null ? year : manufactureYear;
        }

        public Long resolvedBrandId() {
            return parseId(brandId);
        }

        public Long resolvedModelId() {
            return parseId(modelId);
        }

        private static Long parseId(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Long.valueOf(value.trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }

    public static class VehicleFormRequest {
        private String licensePlate;
        private String brand;
        private String model;
        private String color;
        private Integer manufactureYear;
        private Integer year;
        private String carType;
        private String brandId;
        private String modelId;
        private String suggestedBrandName;
        private String suggestedModelName;

        public String getLicensePlate() {
            return licensePlate;
        }

        public void setLicensePlate(String licensePlate) {
            this.licensePlate = licensePlate;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public Integer getManufactureYear() {
            return manufactureYear;
        }

        public void setManufactureYear(Integer manufactureYear) {
            this.manufactureYear = manufactureYear;
        }

        public Integer getYear() {
            return year;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public String getCarType() {
            return carType;
        }

        public void setCarType(String carType) {
            this.carType = carType;
        }

        public String getBrandId() {
            return brandId;
        }

        public void setBrandId(String brandId) {
            this.brandId = brandId;
        }

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public String getSuggestedBrandName() {
            return suggestedBrandName;
        }

        public void setSuggestedBrandName(String suggestedBrandName) {
            this.suggestedBrandName = suggestedBrandName;
        }

        public String getSuggestedModelName() {
            return suggestedModelName;
        }

        public void setSuggestedModelName(String suggestedModelName) {
            this.suggestedModelName = suggestedModelName;
        }

        public VehicleRequest toRequest() {
            return new VehicleRequest(
                    licensePlate, brand, model, color, manufactureYear, year, carType,
                    brandId, modelId, suggestedBrandName, suggestedModelName);
        }
    }

    public record VehicleResponse(
            Long id,
            @JsonProperty("_id") String uid,
            Long customerId,
            String licensePlate,
            String brand,
            String model,
            String color,
            Integer manufactureYear,
            Integer year,
            String carType,
            List<Object> images,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String verificationStatus,
            Long brandId,
            Long modelId) {
        public static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(
                    vehicle.getId(),
                    String.valueOf(vehicle.getId()),
                    vehicle.getCustomer().getId(),
                    vehicle.getLicensePlate(),
                    vehicle.getBrand(),
                    vehicle.getModel(),
                    vehicle.getColor(),
                    vehicle.getManufactureYear(),
                    vehicle.getManufactureYear(),
                    "sedan",
                    List.of(),
                    vehicle.isActive() ? null : vehicle.getOwnershipEndAt(),
                    vehicle.getCreatedAt(),
                    vehicle.getUpdatedAt(),
                    vehicle.getVerificationStatus() == null
                            ? "pending"
                            : vehicle.getVerificationStatus().name().toLowerCase(),
                    vehicle.getBrandRef() == null ? null : vehicle.getBrandRef().getId(),
                    vehicle.getModelRef() == null ? null : vehicle.getModelRef().getId());
        }
    }
}
