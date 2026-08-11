package com.shinecraft.server.user;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Users")
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    ApiResponse<List<UserAdminDtos.UserSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok("Users retrieved successfully", userAdminService.search(keyword, role, active));
    }

    @GetMapping("/{id}")
    ApiResponse<UserAdminDtos.UserSearchResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok("User retrieved successfully", userAdminService.detail(id));
    }

    @PatchMapping("/{id}/status")
    ApiResponse<UserAdminDtos.UserSearchResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UserAdminDtos.UpdateUserStatusRequest request) {
        return ApiResponse.ok("User status updated successfully", userAdminService.updateStatus(id, request.active()));
    }

    @PatchMapping("/{id}/role")
    ApiResponse<UserAdminDtos.UserSearchResponse> updateRole(
            @PathVariable Long id, @Valid @RequestBody UserAdminDtos.UpdateUserRoleRequest request) {
        return ApiResponse.ok("User role updated successfully", userAdminService.updateRole(id, request.role()));
    }

    @PostMapping("/{id}/reset-password")
    ApiResponse<Void> resetPassword(
            @PathVariable Long id, @Valid @RequestBody UserAdminDtos.ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request.newPassword());
        return ApiResponse.ok("User password reset successfully", null);
    }
}
