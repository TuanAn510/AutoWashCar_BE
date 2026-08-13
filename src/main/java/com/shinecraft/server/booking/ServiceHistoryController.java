package com.shinecraft.server.booking;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shinecraft.server.common.ApiListResponse;
import com.shinecraft.server.common.ApiResponse;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserDtos;
import com.shinecraft.server.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class ServiceHistoryController {
    private final BookingRepository bookingRepository;
    private final AuthService authService;

    public ServiceHistoryController(BookingRepository bookingRepository, AuthService authService) {
        this.bookingRepository = bookingRepository;
        this.authService = authService;
    }

    @GetMapping("/api/service-histories")
    @Transactional(readOnly = true)
    ApiListResponse<ServiceHistoryResponse> serviceHistories(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long appointmentId) {
        requireAdmin(authService.currentUser());
        return ApiListResponse.ok(
                "Service histories retrieved successfully",
                bookingRepository.findAll().stream()
                        .filter(booking -> customerId == null || booking.getCustomer().getId().equals(customerId))
                        .filter(booking -> vehicleId == null || booking.getVehicle().getId().equals(vehicleId))
                        .filter(booking -> appointmentId == null || booking.getId().equals(appointmentId))
                        .map(ServiceHistoryResponse::from)
                        .toList());
    }

    @GetMapping("/api/service-histories/my")
    @Transactional(readOnly = true)
    ApiListResponse<ServiceHistoryResponse> myServiceHistories() {
        return ApiListResponse.ok(
                "My service histories retrieved successfully",
                bookingRepository.findByCustomerOrderByScheduledAtDesc(authService.currentUser()).stream()
                        .map(ServiceHistoryResponse::from)
                        .toList());
    }

    @GetMapping("/api/service-histories/staff/my")
    @Transactional(readOnly = true)
    ApiListResponse<ServiceHistoryResponse> staffServiceHistories() {
        User staff = authService.currentUser();
        List<Booking> bookings = staff.getRole() == UserRole.ROLE_ADMIN
                ? bookingRepository.findAll()
                : bookingRepository.findByAssignedStaffOrderByScheduledAtDesc(staff);
        return ApiListResponse.ok(
                "Staff service histories retrieved successfully",
                bookings.stream().map(ServiceHistoryResponse::from).toList());
    }

    @GetMapping("/api/service-histories/my/vehicles/{vehicleId}")
    @Transactional(readOnly = true)
    ApiListResponse<ServiceHistoryResponse> myVehicleServiceHistories(@PathVariable Long vehicleId) {
        return ApiListResponse.ok(
                "Vehicle service histories retrieved successfully",
                bookingRepository.findByCustomerOrderByScheduledAtDesc(authService.currentUser()).stream()
                        .filter(booking -> booking.getVehicle().getId().equals(vehicleId))
                        .map(ServiceHistoryResponse::from)
                        .toList());
    }

    @GetMapping({"/api/service-histories/{id}", "/api/service-histories/my/{id}"})
    @Transactional(readOnly = true)
    ApiResponse<ServiceHistoryResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok("Service history retrieved successfully", ServiceHistoryResponse.from(findBooking(id)));
    }

    @PatchMapping("/api/service-histories/{id}")
    @Transactional
    ApiResponse<ServiceHistoryResponse> update(@PathVariable Long id, @RequestBody UpdateServiceHistoryRequest request) {
        requireAdmin(authService.currentUser());
        Booking booking = findBooking(id);
        if (request.note() != null) {
            booking.setNote(request.note());
        }
        return ApiResponse.ok("Service history updated successfully", ServiceHistoryResponse.from(booking));
    }

    @DeleteMapping("/api/service-histories/{id}")
    ApiResponse<Void> delete(@PathVariable Long id) {
        requireAdmin(authService.currentUser());
        findBooking(id);
        throw new ApiException(HttpStatus.BAD_REQUEST, "Service histories are generated from bookings and cannot be deleted");
    }

    private Booking findBooking(Long id) {
        Booking booking = bookingRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service history not found"));
        requireCanViewBooking(authService.currentUser(), booking);
        return booking;
    }

    private void requireAdmin(User actor) {
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin permission is required");
        }
    }

    private void requireCanViewBooking(User actor, Booking booking) {
        if (actor.getRole() == UserRole.ROLE_ADMIN
                || sameUser(actor, booking.getCustomer())
                || sameUser(actor, booking.getAssignedStaff())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to access this service history");
    }

    private boolean sameUser(User first, User second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getId() != null && second.getId() != null) {
            return first.getId().equals(second.getId());
        }
        return first == second;
    }

    public record UpdateServiceHistoryRequest(String note, String nextMaintenanceDate) {}

    public record ServiceHistoryUserSummary(
            @JsonProperty("_id") String uid,
            String displayName,
            String phone,
            String role) {}

    public record ServiceHistoryVehicleSummary(
            @JsonProperty("_id") String uid,
            String brand,
            String model,
            String licensePlate,
            Integer year) {}

    public record ServiceHistoryAppointmentSummary(
            @JsonProperty("_id") String uid,
            String status,
            LocalDateTime scheduledAt,
            LocalDateTime completedAt,
            String paymentStatus) {}

    public record ServiceHistoryServiceSnapshot(
            String serviceId,
            String nameSnapshot,
            BigDecimal priceSnapshot,
            Integer estimatedDurationSnapshot) {}

    public record ServiceHistoryResponse(
            @JsonProperty("_id") String uid,
            ServiceHistoryUserSummary customerId,
            ServiceHistoryVehicleSummary vehicleId,
            ServiceHistoryAppointmentSummary appointmentId,
            List<ServiceHistoryServiceSnapshot> services,
            BigDecimal totalPrice,
            Integer totalEstimatedDuration,
            LocalDateTime servicedAt,
            ServiceHistoryUserSummary handledBy,
            String note,
            LocalDateTime nextMaintenanceDate,
            boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public static ServiceHistoryResponse from(Booking booking) {
            return new ServiceHistoryResponse(
                    String.valueOf(booking.getId()),
                    new ServiceHistoryUserSummary(
                            String.valueOf(booking.getCustomer().getId()),
                            booking.getCustomer().getFullName(),
                            booking.getCustomer().getPhone(),
                            UserDtos.toFrontendRole(booking.getCustomer().getRole())),
                    new ServiceHistoryVehicleSummary(
                            String.valueOf(booking.getVehicle().getId()),
                            booking.getVehicle().getBrand(),
                            booking.getVehicle().getModel(),
                            booking.getVehicle().getLicensePlate(),
                            booking.getVehicle().getManufactureYear()),
                    new ServiceHistoryAppointmentSummary(
                            String.valueOf(booking.getId()),
                            BookingDtos.toFrontendStatus(booking.getStatus()),
                            booking.getScheduledAt(),
                            booking.getCompletedAt(),
                            booking.getStatus() == BookingStatus.CANCELLED
                                    ? "cancelled"
                                    : booking.getPaymentStatus().name().toLowerCase(java.util.Locale.ROOT)),
                    booking.getServices().stream()
                            .map(service -> new ServiceHistoryServiceSnapshot(
                                    String.valueOf(service.getService().getId()),
                                    service.getServiceName(),
                                    service.getPrice(),
                                    service.getDurationMinutes()))
                            .toList(),
                    booking.getFinalAmount(),
                    booking.getServices().stream().mapToInt(BookingService::getDurationMinutes).sum(),
                    booking.getCompletedAt() == null ? booking.getScheduledAt() : booking.getCompletedAt(),
                    booking.getAssignedStaff() == null ? null : new ServiceHistoryUserSummary(
                            String.valueOf(booking.getAssignedStaff().getId()),
                            booking.getAssignedStaff().getFullName(),
                            booking.getAssignedStaff().getPhone(),
                            UserDtos.toFrontendRole(booking.getAssignedStaff().getRole())),
                    booking.getNote(),
                    null,
                    true,
                    booking.getCreatedAt(),
                    booking.getUpdatedAt());
        }
    }
}
