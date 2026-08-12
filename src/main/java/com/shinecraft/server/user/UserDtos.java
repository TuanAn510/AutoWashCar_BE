package com.shinecraft.server.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class UserDtos {
    private UserDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Pattern(regexp = "^[+0-9\\s.-]{9,20}$") String phone,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 20) String licensePlate,
            @NotBlank @Size(max = 80) String brand,
            @NotBlank @Size(max = 80) String model,
            @Size(max = 40) String color,
            Integer manufactureYear) {}

    public record SignupRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Pattern(regexp = "^[+0-9\\s.-]{9,20}$") String phone,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "^[+0-9\\s.-]{9,20}$") String phone,
            @NotBlank String password) {}

    public record RefreshTokenRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record UserResponse(
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
            LocalDateTime updatedAt) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    String.valueOf(user.getId()),
                    user.getFullName(),
                    user.getFullName(),
                    user.getPhone(),
                    toFrontendRole(user.getRole()),
                    user.getRole().name(),
                    user.isActive(),
                    user.isActive(),
                    user.getCreatedAt(),
                    user.getUpdatedAt());
        }
    }

    public record AuthResponse(String token, String accessToken, String refreshToken, String tokenType, UserResponse user) {
        public AuthResponse(String token, String refreshToken, String tokenType, UserResponse user) {
            this(token, token, refreshToken, tokenType, user);
        }
    }

    public static String toFrontendRole(UserRole role) {
        return switch (role) {
            case ROLE_ADMIN -> "admin";
            case ROLE_CUSTOMER -> "customer";
        };
    }
}
