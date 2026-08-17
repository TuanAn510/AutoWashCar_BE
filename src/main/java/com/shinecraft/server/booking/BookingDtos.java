package com.shinecraft.server.booking;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shinecraft.server.user.UserRole;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class BookingDtos {
    private BookingDtos() {}

    public record CreateBookingRequest(
            @NotNull Long vehicleId,
            List<Long> serviceIds,
            List<ServiceRef> services,
            @NotNull @Future LocalDateTime scheduledAt,
            Long promotionId,
            Long rewardRedemptionId,
            @Size(max = 1000) String note) {
        public CreateBookingRequest(
                Long vehicleId,
                List<Long> serviceIds,
                LocalDateTime scheduledAt,
                Long promotionId,
                Long rewardRedemptionId,
                String note) {
            this(vehicleId, serviceIds, null, scheduledAt, promotionId, rewardRedemptionId, note);
        }

        public List<Long> resolvedServiceIds() {
            if (serviceIds != null && !serviceIds.isEmpty()) {
                return serviceIds;
            }
            if (services == null) {
                return List.of();
            }
            return services.stream().map(ServiceRef::serviceId).toList();
        }
    }

    public record ServiceRef(@NotNull Long serviceId) {}

    public record UpdateStatusRequest(@JsonProperty("status") String statusValue) {
        public UpdateStatusRequest(BookingStatus status) {
            this(status == null ? null : status.name());
        }

        public BookingStatus status() {
            return resolvedStatus();
        }

        public BookingStatus resolvedStatus() {
            return BookingStatus.valueOf(statusValue.trim().toUpperCase());
        }
    }

    public record SlotResponse(LocalDateTime startAt, boolean available, String reason) {}

    public record AvailabilityResponse(String date, Integer bookingWindowDays, List<SlotResponse> slots) {}

    public record CandidateAvailabilityResponse(
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean available,
            String reason,
            LocalDateTime nearestAvailableStartAt) {}

    public record CreatePaymentRequest(@NotNull String method) {
        public BookingPaymentMethod resolvedMethod() {
            return parsePaymentMethod(method);
        }
    }

    public record PaymentResponse(
            String paymentUrl,
            String paymentId,
            String method,
            BigDecimal amount,
            LocalDateTime expiresAt,
            String qrCodeUrl) {}

    public record UpdatePaymentStatusRequest(String paymentStatus, String paymentMethod) {
        public BookingPaymentStatus resolvedPaymentStatus() {
            if (paymentStatus == null || paymentStatus.isBlank()) {
                return BookingPaymentStatus.PAID;
            }
            return BookingPaymentStatus.valueOf(normalizeEnum(paymentStatus));
        }

        public BookingPaymentMethod resolvedPaymentMethod(BookingPaymentMethod fallback) {
            if (paymentMethod == null || paymentMethod.isBlank()) {
                return fallback;
            }
            return parsePaymentMethod(paymentMethod);
        }
    }

    public record AssignStaffRequest(Long staffId, List<Long> staffIds) {
        public AssignStaffRequest(Long staffId) {
            this(staffId, null);
        }

        public List<Long> resolvedStaffIds() {
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            if (staffIds != null) {
                staffIds.stream()
                        .filter(id -> id != null)
                        .forEach(ids::add);
            }
            if (staffId != null) {
                ids.add(staffId);
            }
            return List.copyOf(ids);
        }
    }

    public record RescheduleRequest(@NotNull @Future LocalDateTime scheduledAt) {}

    public record BookingServiceResponse(Long serviceId, String serviceName, BigDecimal price, Integer durationMinutes) {
        public static BookingServiceResponse from(BookingService service) {
            return new BookingServiceResponse(
                    service.getService().getId(), service.getServiceName(), service.getPrice(), service.getDurationMinutes());
        }
    }

    public record BookingResponse(
            Long id,
            Long customerId,
            String customerName,
            Long vehicleId,
            String licensePlate,
            LocalDateTime scheduledAt,
            BookingStatus status,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            Integer earnedPoints,
            String note,
            LocalDateTime completedAt,
            List<BookingServiceResponse> services) {
        public static BookingResponse from(Booking booking) {
            return new BookingResponse(
                    booking.getId(),
                    booking.getCustomer().getId(),
                    booking.getCustomer().getFullName(),
                    booking.getVehicle().getId(),
                    booking.getVehicle().getLicensePlate(),
                    booking.getScheduledAt(),
                    booking.getStatus(),
                    booking.getSubtotalAmount(),
                    booking.getDiscountAmount(),
                    booking.getFinalAmount(),
                    booking.getEarnedPoints(),
                    booking.getNote(),
                    booking.getCompletedAt(),
                    booking.getServices().stream().map(BookingServiceResponse::from).toList());
        }
    }

    public record QueueItemResponse(
            Long bookingId,
            LocalDateTime scheduledAt,
            String customerName,
            String licensePlate,
            String tierName,
            Integer priorityLevel,
            BookingStatus status,
            BigDecimal finalAmount,
            LocalDateTime checkInAt,
            Long waitingMinutes,
            Integer serviceDurationMinutes,
            Integer position) {}

    public record AppointmentUser(
            @JsonProperty("_id") String uid,
            String displayName,
            String phone,
            String role) {}

    public record AppointmentVehicle(
            @JsonProperty("_id") String uid,
            String brand,
            String model,
            String licensePlate,
            Integer year,
            String carType) {}

    public record AppointmentServiceSnapshot(
            String serviceId,
            String nameSnapshot,
            BigDecimal priceSnapshot,
            Integer estimatedDurationSnapshot) {}

    public record AppointmentResponse(
            @JsonProperty("_id") String uid,
            AppointmentUser customerId,
            AppointmentVehicle vehicleId,
            Object assignedStaffId,
            List<AppointmentUser> assignedStaffIds,
            Object cancelledBy,
            List<AppointmentServiceSnapshot> services,
            LocalDateTime scheduledAt,
            String note,
            String status,
            Integer totalEstimatedDuration,
            BigDecimal subtotalPrice,
            BigDecimal discountAmount,
            BigDecimal totalPrice,
            BigDecimal finalAmount,
            String paymentMethod,
            String paymentStatus,
            String cancelReason,
            LocalDateTime cancelledAt,
            LocalDateTime completedAt,
            Integer pointsEarned,
            boolean isPointsAwarded,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public static AppointmentResponse from(Booking booking) {
            return new AppointmentResponse(
                    String.valueOf(booking.getId()),
                    new AppointmentUser(
                            String.valueOf(booking.getCustomer().getId()),
                            booking.getCustomer().getFullName(),
                            booking.getCustomer().getPhone(),
                            toFrontendRole(booking.getCustomer().getRole())),
                    new AppointmentVehicle(
                            String.valueOf(booking.getVehicle().getId()),
                            booking.getVehicle().getBrand(),
                            booking.getVehicle().getModel(),
                            booking.getVehicle().getLicensePlate(),
                            booking.getVehicle().getManufactureYear(),
                            "sedan"),
                    primaryAssignedStaff(booking),
                    assignedStaffs(booking),
                    null,
                    booking.getServices().stream()
                            .map(service -> new AppointmentServiceSnapshot(
                                    service.getService() == null ? null : String.valueOf(service.getService().getId()),
                                    service.getServiceName(),
                                    service.getPrice(),
                                    service.getDurationMinutes()))
                            .toList(),
                    booking.getScheduledAt(),
                    booking.getNote(),
                    toFrontendStatus(booking.getStatus()),
                    booking.getServices().stream().mapToInt(BookingService::getDurationMinutes).sum(),
                    booking.getSubtotalAmount(),
                    booking.getDiscountAmount(),
                    booking.getFinalAmount(),
                    booking.getFinalAmount(),
                    booking.getPaymentMethod().name().toLowerCase(Locale.ROOT),
                    booking.getStatus() == BookingStatus.CANCELLED
                            ? "cancelled"
                            : booking.getPaymentStatus().name().toLowerCase(Locale.ROOT),
                    null,
                    booking.getStatus() == BookingStatus.CANCELLED ? booking.getUpdatedAt() : null,
                    booking.getCompletedAt(),
                    booking.getEarnedPoints(),
                    booking.getEarnedPoints() != null && booking.getEarnedPoints() > 0,
                    booking.getCreatedAt(),
                    booking.getUpdatedAt());
        }

        private static AppointmentUser primaryAssignedStaff(Booking booking) {
            return booking.getAssignedStaff() == null ? null : fromUser(booking.getAssignedStaff());
        }

        private static List<AppointmentUser> assignedStaffs(Booking booking) {
            return java.util.stream.Stream.of(booking.getAssignedStaff(), booking.getSecondaryAssignedStaff())
                    .filter(user -> user != null)
                    .map(AppointmentResponse::fromUser)
                    .toList();
        }

        private static AppointmentUser fromUser(com.shinecraft.server.user.User user) {
            return new AppointmentUser(
                    String.valueOf(user.getId()),
                    user.getFullName(),
                    user.getPhone(),
                    toFrontendRole(user.getRole()));
        }
    }

    public record AppointmentStatusSummary(
            long total,
            long pending,
            long confirmed,
            long inQueue,
            long inProgress,
            long completed,
            long cancelled) {
        public static AppointmentStatusSummary from(List<Booking> bookings) {
            return new AppointmentStatusSummary(
                    bookings.size(),
                    count(bookings, BookingStatus.PENDING),
                    count(bookings, BookingStatus.CONFIRMED),
                    count(bookings, BookingStatus.IN_QUEUE),
                    count(bookings, BookingStatus.IN_PROGRESS),
                    count(bookings, BookingStatus.COMPLETED),
                    count(bookings, BookingStatus.CANCELLED));
        }

        private static long count(List<Booking> bookings, BookingStatus status) {
            return bookings.stream().filter(booking -> booking.getStatus() == status).count();
        }
    }

    public static String toFrontendStatus(BookingStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case CONFIRMED -> "confirmed";
            case IN_QUEUE -> "in_queue";
            case IN_PROGRESS -> "in_progress";
            case COMPLETED -> "completed";
            case CANCELLED -> "cancelled";
        };
    }

    private static String toFrontendRole(UserRole role) {
        return switch (role) {
            case ROLE_ADMIN -> "admin";
            case ROLE_STAFF -> "staff";
            case ROLE_CUSTOMER -> "customer";
        };
    }

    private static BookingPaymentMethod parsePaymentMethod(String value) {
        return BookingPaymentMethod.valueOf(normalizeEnum(value));
    }

    private static String normalizeEnum(String value) {
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    public record AppointmentFilterParams(
            String search,
            String status,
            String staffId,
            String dateFrom,
            String dateTo,
            int page,
            int limit,
            String sortBy,
            String sortOrder) {}

    public record PaginationMeta(
            int page,
            int limit,
            long total,
            int totalPages) {}

    public record AppointmentPageResponse(
            List<AppointmentResponse> appointments,
            PaginationMeta pagination,
            AppointmentStatusSummary summary) {}
}
