package com.shinecraft.server.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.booking.BookingRepository;
import com.shinecraft.server.booking.BookingStatus;
import com.shinecraft.server.common.ApiListResponse;
import com.shinecraft.server.common.ApiResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final BookingRepository bookingRepository;

    public FrontendUserController(
            AuthService authService,
            UserAdminService userAdminService,
            UserRepository userRepository,
            BookingRepository bookingRepository) {
        this.authService = authService;
        this.userAdminService = userAdminService;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
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
    ApiResponse<List<StaffWorkloadResponse>> workload() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime weekStartAt = weekStart.atStartOfDay();
        LocalDateTime nextWeekStartAt = weekStart.plusWeeks(1).atStartOfDay();

        Map<Long, List<Booking>> bookingsByStaff = bookingRepository.findByAssignedStaffIsNotNull().stream()
                .collect(Collectors.groupingBy(booking -> booking.getAssignedStaff().getId()));

        List<StaffWorkloadResponse> workloads = userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_STAFF).stream()
                .map(staff -> StaffWorkloadResponse.from(
                        staff,
                        bookingsByStaff.getOrDefault(staff.getId(), List.of()),
                        todayStart,
                        tomorrowStart,
                        weekStartAt,
                        nextWeekStartAt))
                .toList();

        return ApiResponse.ok("Staff workload retrieved successfully", workloads);
    }

    @GetMapping("/api/users/{id}")
    ApiResponse<UserAdminDtos.UserSearchResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok("User retrieved successfully", userAdminService.detail(id));
    }

    @PatchMapping("/api/users/{id}")
    ApiResponse<UserAdminDtos.UserSearchResponse> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok("User updated successfully", userAdminService.updateFromFrontend(id, payload));
    }

    public record StaffWorkloadResponse(
            @JsonProperty("_id") String uid,
            String displayName,
            String phone,
            String avatarUrl,
            boolean isActive,
            long todayCount,
            long weekCount,
            long activeCount,
            long completedCount) {
        private static final Set<BookingStatus> ACTIVE_STATUSES = Set.of(
                BookingStatus.PENDING,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_QUEUE,
                BookingStatus.IN_PROGRESS);

        static StaffWorkloadResponse from(
                User staff,
                List<Booking> bookings,
                LocalDateTime todayStart,
                LocalDateTime tomorrowStart,
                LocalDateTime weekStart,
                LocalDateTime nextWeekStart) {
            return new StaffWorkloadResponse(
                    String.valueOf(staff.getId()),
                    staff.getFullName(),
                    staff.getPhone(),
                    null,
                    staff.isActive(),
                    countScheduledBetween(bookings, todayStart, tomorrowStart),
                    countScheduledBetween(bookings, weekStart, nextWeekStart),
                    bookings.stream().filter(booking -> ACTIVE_STATUSES.contains(booking.getStatus())).count(),
                    bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.COMPLETED).count());
        }

        private static long countScheduledBetween(List<Booking> bookings, LocalDateTime start, LocalDateTime end) {
            return bookings.stream()
                    .filter(booking -> !booking.getScheduledAt().isBefore(start) && booking.getScheduledAt().isBefore(end))
                    .count();
        }
    }
}
