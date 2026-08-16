package com.shinecraft.server.booking;

import com.shinecraft.server.common.ApiResponse;
import com.shinecraft.server.common.ApiSummaryListResponse;
import com.shinecraft.server.common.PaginationMeta;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookings")
@RestController
public class BookingController {
    private final BookingServiceLayer bookingService;

    public BookingController(BookingServiceLayer bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/api/bookings")
    ApiResponse<BookingDtos.BookingResponse> create(@Valid @RequestBody BookingDtos.CreateBookingRequest request) {
        return ApiResponse.ok("Booking created successfully", bookingService.create(request));
    }

    @PostMapping("/api/appointments")
    ApiResponse<BookingDtos.AppointmentResponse> createAppointment(
            @Valid @RequestBody BookingDtos.CreateBookingRequest request) {
        return ApiResponse.ok("Appointment created successfully", bookingService.createAppointment(request));
    }

    @GetMapping("/api/bookings/my")
    ApiResponse<List<BookingDtos.BookingResponse>> myBookings() {
        return ApiResponse.ok("My bookings retrieved successfully", bookingService.myBookings());
    }

    @GetMapping("/api/appointments/my")
    ApiSummaryListResponse<BookingDtos.AppointmentResponse, BookingDtos.AppointmentStatusSummary> myAppointments() {
        List<BookingDtos.AppointmentResponse> appointments = bookingService.myAppointments();
        return ApiSummaryListResponse.ok(
                "My appointments retrieved successfully",
                appointments,
                summarizeAppointmentResponses(appointments));
    }

    @GetMapping("/api/appointments/staff/my")
    ApiSummaryListResponse<BookingDtos.AppointmentResponse, BookingDtos.AppointmentStatusSummary> myStaffAppointments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String staffId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "scheduledAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        BookingDtos.AppointmentFilterParams params = new BookingDtos.AppointmentFilterParams(
                search, status, staffId, dateFrom, dateTo, page, limit, sortBy, sortOrder);
        BookingDtos.AppointmentPageResponse result = bookingService.myStaffAppointments(params);
        return new ApiSummaryListResponse<>(
                true,
                "My staff appointments retrieved successfully",
                result.appointments(),
                new PaginationMeta(
                        result.pagination().page(),
                        result.pagination().limit(),
                        result.pagination().total(),
                        result.pagination().totalPages()),
                result.summary());
    }

    @GetMapping("/api/appointments")
    ApiSummaryListResponse<BookingDtos.AppointmentResponse, BookingDtos.AppointmentStatusSummary> appointments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String staffId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "scheduledAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        BookingDtos.AppointmentFilterParams params = new BookingDtos.AppointmentFilterParams(
                search, status, staffId, dateFrom, dateTo, page, limit, sortBy, sortOrder);
        BookingDtos.AppointmentPageResponse result = bookingService.allAppointments(params);
        return new ApiSummaryListResponse<>(
                true,
                "Appointments retrieved successfully",
                result.appointments(),
                new PaginationMeta(
                        result.pagination().page(),
                        result.pagination().limit(),
                        result.pagination().total(),
                        result.pagination().totalPages()),
                result.summary());
    }

    @GetMapping("/api/appointments/{id}")
    ApiResponse<BookingDtos.AppointmentResponse> appointmentDetail(@PathVariable Long id) {
        return ApiResponse.ok("Appointment retrieved successfully", bookingService.appointmentDetail(id));
    }

    @PatchMapping("/api/appointments/{id}/status")
    ApiResponse<BookingDtos.AppointmentResponse> updateAppointmentStatus(
            @PathVariable Long id, @Valid @RequestBody BookingDtos.UpdateStatusRequest request) {
        return ApiResponse.ok(
                "Appointment status updated successfully",
                bookingService.updateAppointmentStatus(id, request.resolvedStatus()));
    }

    @PatchMapping("/api/appointments/my/{id}/cancel")
    ApiResponse<BookingDtos.AppointmentResponse> cancelMyAppointment(@PathVariable Long id) {
        return ApiResponse.ok(
                "Appointment cancelled successfully",
                bookingService.updateAppointmentStatus(id, BookingStatus.CANCELLED));
    }

    @PatchMapping("/api/appointments/{id}/cancel")
    ApiResponse<BookingDtos.AppointmentResponse> cancelAppointment(@PathVariable Long id) {
        return ApiResponse.ok(
                "Appointment cancelled successfully",
                bookingService.updateAppointmentStatus(id, BookingStatus.CANCELLED));
    }

    @PatchMapping("/api/appointments/{id}/payment-status")
    ApiResponse<BookingDtos.AppointmentResponse> updatePaymentStatus(
            @PathVariable Long id, @RequestBody(required = false) BookingDtos.UpdatePaymentStatusRequest request) {
        return ApiResponse.ok("Payment status updated successfully", bookingService.updatePaymentStatus(id, request));
    }

    @PostMapping("/api/appointments/{id}/payment")
    ApiResponse<BookingDtos.PaymentResponse> createPayment(
            @PathVariable Long id, @Valid @RequestBody BookingDtos.CreatePaymentRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        return ApiResponse.ok("Payment created successfully", bookingService.createPayment(id, request, clientIp));
    }

    @PatchMapping("/api/appointments/{id}/assign-staff")
    ApiResponse<BookingDtos.AppointmentResponse> assignStaff(
            @PathVariable Long id, @Valid @RequestBody BookingDtos.AssignStaffRequest request) {
        return ApiResponse.ok("Staff assigned successfully", bookingService.assignStaff(id, request));
    }

    @PatchMapping("/api/appointments/{id}/reschedule")
    ApiResponse<BookingDtos.AppointmentResponse> reschedule(
            @PathVariable Long id, @Valid @RequestBody BookingDtos.RescheduleRequest request) {
        return ApiResponse.ok("Appointment rescheduled successfully", bookingService.reschedule(id, request));
    }

    @GetMapping("/api/bookings/availability")
    ApiResponse<BookingDtos.AvailabilityResponse> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long rewardRedemptionId) {
        return ApiResponse.ok(
                "Booking availability retrieved successfully",
                bookingService.availability(date, vehicleId, serviceId, rewardRedemptionId));
    }

    @GetMapping("/api/bookings/availability/check")
    ApiResponse<BookingDtos.CandidateAvailabilityResponse> checkAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
            @RequestParam Long vehicleId,
            @RequestParam Long serviceId,
            @RequestParam(required = false) Long rewardRedemptionId) {
        return ApiResponse.ok(
                "Booking candidate availability retrieved successfully",
                bookingService.checkAvailability(scheduledAt, vehicleId, serviceId, rewardRedemptionId));
    }

    @GetMapping("/api/admin/bookings/today")
    ApiResponse<List<BookingDtos.BookingResponse>> today() {
        return ApiResponse.ok("Today's bookings retrieved successfully", bookingService.todayBookings());
    }

    @GetMapping("/api/admin/bookings/priority-queue")
    ApiResponse<List<BookingDtos.QueueItemResponse>> queue() {
        return ApiResponse.ok("Priority queue retrieved successfully", bookingService.priorityQueue());
    }

    @PatchMapping("/api/admin/bookings/{id}/status")
    ApiResponse<BookingDtos.BookingResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody BookingDtos.UpdateStatusRequest request) {
        return ApiResponse.ok("Booking status updated successfully", bookingService.updateStatus(id, request.resolvedStatus()));
    }

    private BookingDtos.AppointmentStatusSummary summarizeAppointmentResponses(
            List<BookingDtos.AppointmentResponse> appointments) {
        return new BookingDtos.AppointmentStatusSummary(
                appointments.size(),
                appointments.stream().filter(appointment -> appointment.status().equals("pending")).count(),
                appointments.stream().filter(appointment -> appointment.status().equals("confirmed")).count(),
                appointments.stream().filter(appointment -> appointment.status().equals("in_progress")).count(),
                appointments.stream().filter(appointment -> appointment.status().equals("completed")).count(),
                appointments.stream().filter(appointment -> appointment.status().equals("cancelled")).count());
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
