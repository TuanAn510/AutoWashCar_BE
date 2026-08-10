package com.shinecraft.server.booking;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
        return ApiResponse.ok("Dat lich thanh cong", bookingService.create(request));
    }

    @GetMapping("/api/bookings/my")
    ApiResponse<List<BookingDtos.BookingResponse>> myBookings() {
        return ApiResponse.ok("Lay booking cua toi thanh cong", bookingService.myBookings());
    }

    @GetMapping("/api/bookings/availability")
    ApiResponse<BookingDtos.AvailabilityResponse> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok("Lay slot dat lich thanh cong", bookingService.availability(date));
    }

    @GetMapping("/api/admin/bookings/today")
    ApiResponse<List<BookingDtos.BookingResponse>> today() {
        return ApiResponse.ok("Lay booking trong ngay thanh cong", bookingService.todayBookings());
    }

    @GetMapping("/api/admin/bookings/priority-queue")
    ApiResponse<List<BookingDtos.QueueItemResponse>> queue() {
        return ApiResponse.ok("Lay hang doi uu tien thanh cong", bookingService.priorityQueue());
    }

    @PatchMapping("/api/admin/bookings/{id}/status")
    ApiResponse<BookingDtos.BookingResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody BookingDtos.UpdateStatusRequest request) {
        return ApiResponse.ok("Cap nhat trang thai booking thanh cong", bookingService.updateStatus(id, request.status()));
    }
}
