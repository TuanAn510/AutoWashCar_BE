package com.shinecraft.server.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class VehicleDtos {
    private VehicleDtos() {}

    public record VehicleRequest(
            @NotBlank @Size(max = 20) String licensePlate,
            @NotBlank @Size(max = 80) String brand,
            @NotBlank @Size(max = 80) String model,
            @Size(max = 40) String color,
            Integer manufactureYear) {}

    public record VehicleResponse(
            Long id, String licensePlate, String brand, String model, String color, Integer manufactureYear) {
        public static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(
                    vehicle.getId(),
                    vehicle.getLicensePlate(),
                    vehicle.getBrand(),
                    vehicle.getModel(),
                    vehicle.getColor(),
                    vehicle.getManufactureYear());
        }
    }
}
