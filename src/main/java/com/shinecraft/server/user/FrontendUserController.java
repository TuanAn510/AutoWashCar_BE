package com.shinecraft.server.user;

import com.shinecraft.server.common.ApiListResponse;
import com.shinecraft.server.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FrontendUserController {
    private final AuthService authService;
    private final UserAdminService userAdminService;
    private final UserRepository userRepository;

    public FrontendUserController(AuthService authService, UserAdminService userAdminService, UserRepository userRepository) {
        this.authService = authService;
        this.userAdminService = userAdminService;
        this.userRepository = userRepository;
    }

    @PatchMapping("/api/users/me")
    ApiResponse<UserDtos.UserResponse> updateMe(@RequestBody Map<String, Object> payload) {
        User user = authService.currentUser();
        if (payload.containsKey("displayName") && payload.get("displayName") instanceof String displayName && !displayName.isBlank()) {
            user.setFullName(displayName.trim());
        }
        return ApiResponse.ok("Profile updated successfully", UserDtos.UserResponse.from(userRepository.save(user)));
    }

    @GetMapping("/api/users")
    ApiListResponse<UserAdminDtos.UserSearchResponse> users(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive) {
        return ApiListResponse.ok(
                "Users retrieved successfully",
                userAdminService.search(search != null ? search : keyword, UserRole.ROLE_CUSTOMER, isActive));
    }

    @GetMapping("/api/users/staffs")
    ApiListResponse<UserAdminDtos.UserSearchResponse> staffs() {
        return ApiListResponse.ok("Staffs retrieved successfully", userAdminService.search(null, UserRole.ROLE_STAFF, true));
    }

    @GetMapping("/api/users/staffs/workload")
    ApiResponse<List<Object>> workload() {
        return ApiResponse.ok("Staff workload retrieved successfully", List.of());
    }

    @GetMapping("/api/users/{id}")
    ApiResponse<UserAdminDtos.UserSearchResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok("User retrieved successfully", userAdminService.detail(id));
    }

    @PatchMapping("/api/users/{id}")
    ApiResponse<UserAdminDtos.UserSearchResponse> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok("User updated successfully", userAdminService.updateFromFrontend(id, payload));
    }
}
