package com.shinecraft.server.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Pattern(regexp = "^[0-9]{9,15}$") String phone,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 20) String licensePlate,
            @NotBlank @Size(max = 80) String brand,
            @NotBlank @Size(max = 80) String model,
            @Size(max = 40) String color,
            Integer manufactureYear) {}

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{9,15}$") String phone,
            @NotBlank String password) {}

    public record UserResponse(Long id, String fullName, String phone, UserRole role, boolean active) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getPhone(), user.getRole(), user.isActive());
        }
    }

    public record AuthResponse(String token, UserResponse user) {}
}
