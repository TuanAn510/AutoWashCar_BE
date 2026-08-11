package com.shinecraft.server.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyService;
import com.shinecraft.server.loyalty.RewardRedemptionRepository;
import com.shinecraft.server.promotion.PromotionService;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingServiceLayerTest {
    private BookingRepository bookingRepository;
    private VehicleRepository vehicleRepository;
    private LoyaltyService loyaltyService;
    private AuthService authService;
    private BookingServiceLayer bookingService;
    private User customer;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        vehicleRepository = mock(VehicleRepository.class);
        loyaltyService = mock(LoyaltyService.class);
        authService = mock(AuthService.class);
        bookingService = new BookingServiceLayer(
                bookingRepository,
                vehicleRepository,
                mock(CarWashServiceRepository.class),
                mock(RewardRedemptionRepository.class),
                loyaltyService,
                mock(PromotionService.class),
                authService,
                10000);

        customer = new User();
        account = new LoyaltyAccount();
        when(authService.currentUser()).thenReturn(customer);
        when(loyaltyService.getOrCreateAccount(customer)).thenReturn(account);
    }

    @Test
    void availabilityMarksPendingSlotAsBooked() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(8, 0), BookingStatus.PENDING)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), false, "BOOKED");
    }

    @Test
    void availabilityMarksCancelledSlotAsBooked() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(8, 0), BookingStatus.CANCELLED)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), false, "BOOKED");
    }

    @Test
    void availabilityMarksCompletedSlotAsBooked() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(8, 0), BookingStatus.COMPLETED)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), false, "BOOKED");
    }

    @Test
    void availabilityMarksUnusedFutureSlotAsAvailable() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), true, null);
    }

    @Test
    void createRejectsAUsedSlotRegardlessOfBookingStatus() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(9, 0);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(bookingRepository.existsByScheduledAt(scheduledAt)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("This booking slot is already reserved");
        verify(bookingRepository).existsByScheduledAt(scheduledAt);
    }

    @Test
    void createRejectsAnOutOfHoursSlot() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(17, 0);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking slot must be between 08:00 and 17:00 and aligned to 30-minute intervals");
    }

    @Test
    void createRetainsMembershipBookingWindowValidation() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(8).atTime(9, 0);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Current membership tier can only book up to 7 days in advance");
    }

    private BookingDtos.CreateBookingRequest requestAt(LocalDateTime scheduledAt) {
        return new BookingDtos.CreateBookingRequest(1L, List.of(1L), scheduledAt, null, null, null);
    }

    private Booking bookingAt(LocalDate date, LocalTime time, BookingStatus status) {
        Booking booking = new Booking();
        booking.setScheduledAt(date.atTime(time));
        booking.setStatus(status);
        return booking;
    }

    private void assertSlot(
            BookingDtos.AvailabilityResponse response, LocalDate date, LocalTime time, boolean available, String reason) {
        BookingDtos.SlotResponse slot = response.slots().stream()
                .filter(candidate -> candidate.startAt().equals(date.atTime(time)))
                .findFirst()
                .orElseThrow();
        assertThat(slot.available()).isEqualTo(available);
        assertThat(slot.reason()).isEqualTo(reason);
    }
}
