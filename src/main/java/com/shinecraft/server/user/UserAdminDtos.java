package com.shinecraft.server.user;

import com.fasterxml.jackson.annotation.JsonProperty;
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
            @JsonProperty("_id") String uid,
            String fullName,
            String displayName,
            String phone,
            String role,
            String legacyRole,
            boolean active,
            boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<VehicleSummary> vehicles) {
        public static UserSearchResponse from(User user, List<Vehicle> vehicles) {
            return new UserSearchResponse(
                    user.getId(),
                    String.valueOf(user.getId()),
                    user.getFullName(),
                    user.getFullName(),
                    user.getPhone(),
                    UserDtos.toFrontendRole(user.getRole()),
                    user.getRole().name(),
                    user.isActive(),
                    user.isActive(),
                    user.getCreatedAt(),
                    user.getUpdatedAt(),
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
