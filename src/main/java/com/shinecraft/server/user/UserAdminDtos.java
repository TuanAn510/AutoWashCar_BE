package com.shinecraft.server.user;

import com.shinecraft.server.vehicle.Vehicle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class UserAdminDtos {
    private UserAdminDtos() {}

    public record UserSearchResponse(
            Long id,
            String fullName,
            String phone,
            UserRole role,
            boolean active,
            List<VehicleSummary> vehicles) {
        public static UserSearchResponse from(User user, List<Vehicle> vehicles) {
            return new UserSearchResponse(
                    user.getId(),
                    user.getFullName(),
                    user.getPhone(),
                    user.getRole(),
                    user.isActive(),
                    vehicles.stream().map(VehicleSummary::from).toList());
        }
    }

    public record VehicleSummary(
            Long id,
            String licensePlate,
            String brand,
            String model,
            boolean active,
            LocalDateTime ownershipStartAt,
            LocalDateTime ownershipEndAt) {
        public static VehicleSummary from(Vehicle vehicle) {
            return new VehicleSummary(
                    vehicle.getId(),
                    vehicle.getLicensePlate(),
                    vehicle.getBrand(),
                    vehicle.getModel(),
                    vehicle.isActive(),
                    vehicle.getOwnershipStartAt(),
                    vehicle.getOwnershipEndAt());
        }
    }

    public record UpdateUserStatusRequest(@NotNull Boolean active) {}

    public record UpdateUserRoleRequest(@NotNull UserRole role) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 100) String newPassword) {}
}
