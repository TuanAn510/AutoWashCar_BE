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
            String carType) {
        public Integer resolvedYear() {
            return year != null ? year : manufactureYear;
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

        public VehicleRequest toRequest() {
            return new VehicleRequest(licensePlate, brand, model, color, manufactureYear, year, carType);
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
            LocalDateTime updatedAt) {
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
                    vehicle.getUpdatedAt());
        }
    }
}
