package com.shinecraft.server.booking;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class BookingDtos {
    private BookingDtos() {}

    public record CreateBookingRequest(
            @NotNull Long vehicleId,
            @NotEmpty List<Long> serviceIds,
            @NotNull @Future LocalDateTime scheduledAt,
            Long promotionId,
            Long rewardRedemptionId,
            @Size(max = 1000) String note) {}

    public record UpdateStatusRequest(@NotNull BookingStatus status) {}

    public record SlotResponse(LocalDateTime startAt, boolean available, String reason) {}

    public record AvailabilityResponse(String date, Integer bookingWindowDays, List<SlotResponse> slots) {}

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
}
