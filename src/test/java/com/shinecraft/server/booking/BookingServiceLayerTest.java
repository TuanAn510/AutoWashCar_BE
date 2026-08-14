package com.shinecraft.server.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinecraft.server.audit.AuditTrailService;
import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.catalog.ServiceCategory;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyService;
import com.shinecraft.server.loyalty.MembershipTier;
import com.shinecraft.server.loyalty.Reward;
import com.shinecraft.server.loyalty.RewardRedemption;
import com.shinecraft.server.loyalty.RewardRedemptionRepository;
import com.shinecraft.server.loyalty.RewardRedemptionStatus;
import com.shinecraft.server.payment.VnPayService;
import com.shinecraft.server.promotion.Promotion;
import com.shinecraft.server.promotion.PromotionService;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class BookingServiceLayerTest {
    private BookingRepository bookingRepository;
    private VehicleRepository vehicleRepository;
    private CarWashServiceRepository serviceRepository;
    private RewardRedemptionRepository redemptionRepository;
    private LoyaltyService loyaltyService;
    private PromotionService promotionService;
    private AuthService authService;
    private UserRepository userRepository;
    private BookingServiceLayer bookingService;
    private User customer;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        vehicleRepository = mock(VehicleRepository.class);
        serviceRepository = mock(CarWashServiceRepository.class);
        redemptionRepository = mock(RewardRedemptionRepository.class);
        loyaltyService = mock(LoyaltyService.class);
        promotionService = mock(PromotionService.class);
        authService = mock(AuthService.class);
        userRepository = mock(UserRepository.class);
        bookingService = new BookingServiceLayer(
                bookingRepository,
                vehicleRepository,
                serviceRepository,
                redemptionRepository,
                loyaltyService,
                promotionService,
                authService,
                userRepository,
                mock(AuditTrailService.class),
                mock(VnPayService.class),
                2,
                true,
                10000);

        customer = new User();
        customer.setRole(UserRole.ROLE_ADMIN);
        account = new LoyaltyAccount();
        when(authService.currentUser()).thenReturn(customer);
        when(loyaltyService.getOrCreateAccount(customer)).thenReturn(account);
        when(userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_STAFF))
                .thenReturn(List.of(new User(), new User()));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());
    }

    @Test
    void availabilityUsesFifteenMinuteSuggestionsAndKeepsThirtyMinuteFallbackDuration() {
        LocalDate date = LocalDate.now().plusDays(1);

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertThat(response.slots()).hasSize(36);
        assertThat(response.slots().stream().limit(5).map(BookingDtos.SlotResponse::startAt).toList())
                .containsExactly(
                        date.atTime(8, 0),
                        date.atTime(8, 15),
                        date.atTime(8, 30),
                        date.atTime(8, 45),
                        date.atTime(9, 0));
        assertSlot(response, date, LocalTime.of(16, 45), false, "OUT_OF_BUSINESS_HOURS");
    }

    @Test
    void availabilityKeepsSlotAvailableWhenCapacityRemains() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(8, 0), BookingStatus.PENDING, 30)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), true, null);
    }

    @Test
    void availabilityMarksSlotUnavailableWhenParallelCapacityIsFull() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(
                        bookingAt(date, LocalTime.of(8, 0), BookingStatus.PENDING, 30),
                        bookingAt(date, LocalTime.of(8, 0), BookingStatus.CONFIRMED, 30)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), false, "CAPACITY_FULL");
    }

    @Test
    void availabilityMarksCancelledSlotAsAvailable() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(8, 0), BookingStatus.CANCELLED)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), true, null);
    }

    @Test
    void availabilityMarksCompletedSlotAsAvailable() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(8, 0), BookingStatus.COMPLETED)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), true, null);
    }

    @Test
    void availabilityAllowsSlotsCoveredByANinetyMinuteBookingWhenCapacityRemains() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 0), BookingStatus.PENDING, 90)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(9, 0), true, null);
        assertSlot(response, date, LocalTime.of(9, 30), true, null);
        assertSlot(response, date, LocalTime.of(10, 0), true, null);
        assertSlot(response, date, LocalTime.of(10, 30), true, null);
    }

    @Test
    void availabilityMarksThirtyMinuteSuggestionsOverlappingAFortyFiveMinuteBookingAsBooked() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 0), BookingStatus.PENDING, 45)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(9, 0), true, null);
        assertSlot(response, date, LocalTime.of(9, 30), true, null);
        assertSlot(response, date, LocalTime.of(10, 0), true, null);
    }

    @Test
    void availabilityIgnoresCancelledAndCompletedBookingDurations() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(
                        bookingAt(date, LocalTime.of(9, 0), BookingStatus.CANCELLED, 60),
                        bookingAt(date, LocalTime.of(10, 0), BookingStatus.COMPLETED, 60)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(9, 30), true, null);
        assertSlot(response, date, LocalTime.of(10, 30), true, null);
    }

    @Test
    void availabilityMarksUnusedFutureSlotAsAvailable() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(8, 0), true, null);
    }

    @Test
    void availabilityMarksSuggestionOverlappingAnArbitraryMinuteBookingAsBooked() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 17), BookingStatus.PENDING, 30)));

        BookingDtos.AvailabilityResponse response = bookingService.availability(date);

        assertSlot(response, date, LocalTime.of(9, 30), true, null);
        assertSlot(response, date, LocalTime.of(10, 0), true, null);
    }

    @Test
    void createAllowsSameTimeForDifferentVehiclesWhenCapacityRemains() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(9, 0);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(scheduledAt.toLocalDate(), LocalTime.of(9, 0), BookingStatus.PENDING, 30)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookingService.create(requestAt(scheduledAt))).isNotNull();
    }

    @Test
    void createRejectsWhenParallelCapacityIsFull() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime scheduledAt = date.atTime(9, 0);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(
                        bookingAt(date, LocalTime.of(9, 0), BookingStatus.PENDING, 30),
                        bookingAt(date, LocalTime.of(9, 0), BookingStatus.CONFIRMED, 30)));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking capacity is full for this time range");
    }

    @Test
    void createAllowsRequestSpanningSequentialBookingsWhenCapacityIsNeverExceeded() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime scheduledAt = date.atTime(8, 0);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(60)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(
                        bookingAt(date, LocalTime.of(8, 0), BookingStatus.PENDING, 30),
                        bookingAt(date, LocalTime.of(8, 30), BookingStatus.CONFIRMED, 30)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookingService.create(requestAt(scheduledAt))).isNotNull();
    }

    @Test
    void createRejectsRequestWhenCapacityIsExceededDuringPartOfItsInterval() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime scheduledAt = date.atTime(8, 30);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(45)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(
                        bookingAt(date, LocalTime.of(8, 0), BookingStatus.PENDING, 45),
                        bookingAt(date, LocalTime.of(8, 15), BookingStatus.CONFIRMED, 45)));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking capacity is full for this time range");
    }

    @Test
    void createAllowsSameVehicleWithMoreThanTwoNonOverlappingActiveAppointments() {
        LocalDate date = LocalDate.now().plusDays(1);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(vehicle, List.of(
                        BookingStatus.PENDING,
                        BookingStatus.CONFIRMED,
                        BookingStatus.IN_QUEUE,
                        BookingStatus.IN_PROGRESS)))
                .thenReturn(List.of(
                        bookingAt(date, LocalTime.of(8, 0), BookingStatus.PENDING, 30),
                        bookingAt(date, LocalTime.of(12, 0), BookingStatus.CONFIRMED, 45),
                        bookingAt(date, LocalTime.of(15, 0), BookingStatus.IN_QUEUE, 30)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookingService.create(requestAt(date.atTime(16, 0)))).isNotNull();
    }

    @Test
    void createRejectsStartLessThanThirtyMinutesInAdvance() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(29).withSecond(0).withNano(0);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking must be scheduled at least 30 minutes in advance");
    }

    @Test
    void createAllowsStartMoreThanThirtyMinutesInAdvance() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(9, 23);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookingService.create(requestAt(scheduledAt))).isNotNull();
    }

    @Test
    void createRejectsSelectionThatBecameStaleBeforeSubmission() {
        LocalDateTime selectionTime = LocalDateTime.now().minusMinutes(1);
        LocalDateTime scheduledAt = selectionTime.plusMinutes(30).withSecond(0).withNano(0);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking must be scheduled at least 30 minutes in advance");
    }

    @Test
    void createRejectsAnOutOfHoursSlot() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(17, 0);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking start time must be between 08:00 and 17:00 with minute precision");
    }

    @Test
    void createRejectsZeroPrimaryServices() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(9, 0);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt, List.of())))
                .isInstanceOf(ApiException.class)
                .hasMessage("Exactly one primary service is required");
    }

    @Test
    void createRejectsMoreThanOnePrimaryService() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(9, 0);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt, List.of(1L, 2L))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Exactly one primary service is required");
    }

    @Test
    void createAllowsArbitraryMinuteStartTimesWhenFree() {
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (LocalTime startTime : List.of(
                LocalTime.of(9, 1), LocalTime.of(9, 17), LocalTime.of(10, 43), LocalTime.of(11, 31))) {
            assertThat(bookingService.create(requestAt(LocalDate.now().plusDays(1).atTime(startTime)))).isNotNull();
        }
    }

    @Test
    void createRejectsStartTimeWithSeconds() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(9, 17, 1);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking start time must be between 08:00 and 17:00 with minute precision");
    }

    @Test
    void createRejectsStartTimeWithNanoseconds() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(9, 17).withNano(1);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking start time must be between 08:00 and 17:00 with minute precision");
    }

    @Test
    void createRetainsMembershipBookingWindowValidation() {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(8).atTime(9, 0);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Current membership tier can only book up to 7 days in advance");
    }

    @Test
    void createRejectsSameVehicleOverlapWithAnExistingBookingDuration() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime scheduledAt = date.atTime(9, 30);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(vehicle, List.of(
                        BookingStatus.PENDING,
                        BookingStatus.CONFIRMED,
                        BookingStatus.IN_QUEUE,
                        BookingStatus.IN_PROGRESS)))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 0), BookingStatus.PENDING, 90)));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("This vehicle already has an appointment in that time range");
    }

    @Test
    void createRejectsSameVehicleArbitraryMinuteOverlapWithAnExistingBooking() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime scheduledAt = date.atTime(9, 1);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(vehicle, List.of(
                        BookingStatus.PENDING,
                        BookingStatus.CONFIRMED,
                        BookingStatus.IN_QUEUE,
                        BookingStatus.IN_PROGRESS)))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 17), BookingStatus.PENDING, 30)));

        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("This vehicle already has an appointment in that time range");
    }

    @Test
    void createAllowsSameVehicleStartExactlyWhenAnExistingBookingEnds() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime scheduledAt = date.atTime(9, 47);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(vehicle, List.of(
                        BookingStatus.PENDING,
                        BookingStatus.CONFIRMED,
                        BookingStatus.IN_QUEUE,
                        BookingStatus.IN_PROGRESS)))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 17), BookingStatus.PENDING, 30)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookingService.create(requestAt(scheduledAt))).isNotNull();
    }

    @Test
    void createAllowsAnOverlapWithACancelledBooking() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime scheduledAt = date.atTime(9, 30);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 0), BookingStatus.CANCELLED, 90)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookingService.create(requestAt(scheduledAt))).isNotNull();
    }

    @Test
    void createAllowsAnOverlapWithACompletedBooking() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime scheduledAt = date.atTime(9, 17);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 0), BookingStatus.COMPLETED, 90)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookingService.create(requestAt(scheduledAt))).isNotNull();
    }

    @Test
    void createUsesExactDurationForSameVehicleOverlapAndAllowsBoundaryTouch() {
        LocalDate date = LocalDate.now().plusDays(1);
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(vehicle));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(30)));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(vehicle, List.of(
                        BookingStatus.PENDING,
                        BookingStatus.CONFIRMED,
                        BookingStatus.IN_QUEUE,
                        BookingStatus.IN_PROGRESS)))
                .thenReturn(List.of(bookingAt(date, LocalTime.of(9, 0), BookingStatus.PENDING, 45)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> bookingService.create(requestAt(date.atTime(9, 44))))
                .isInstanceOf(ApiException.class)
                .hasMessage("This vehicle already has an appointment in that time range");
        assertThat(bookingService.create(requestAt(date.atTime(9, 45)))).isNotNull();
    }

    @Test
    void createAllowsThirtyMinuteBookingEndingExactlyAtClosingTime() {
        assertBookingEndTime(LocalTime.of(16, 30), 30, true);
    }

    @Test
    void createRejectsThirtyMinuteBookingEndingAfterClosingTime() {
        assertBookingEndTime(LocalTime.of(16, 31), 30, false);
    }

    @Test
    void createAllowsFortyFiveMinuteBookingEndingExactlyAtClosingTime() {
        assertBookingEndTime(LocalTime.of(16, 15), 45, true);
    }

    @Test
    void createRejectsFortyFiveMinuteBookingEndingAfterClosingTime() {
        assertBookingEndTime(LocalTime.of(16, 16), 45, false);
    }

    @Test
    void createAllowsNinetyMinuteBookingEndingExactlyAtClosingTime() {
        assertBookingEndTime(LocalTime.of(15, 30), 90, true);
    }

    @Test
    void createRejectsNinetyMinuteBookingEndingAfterClosingTime() {
        assertBookingEndTime(LocalTime.of(15, 31), 90, false);
    }

    @Test
    void createIncludesAddOnExactDurationInReservationTime() {
        LocalDate date = LocalDate.now().plusDays(1);
        CarWashService primaryService = serviceWithDuration(45);
        primaryService.setId(1L);
        CarWashService addOnService = serviceWithDuration(15);
        addOnService.setId(2L);
        Reward reward = new Reward();
        reward.setId(1L);
        reward.setRewardType(com.shinecraft.server.loyalty.RewardType.ADD_ON);
        reward.setAddOnService(addOnService);
        RewardRedemption redemption = new RewardRedemption();
        redemption.setCustomer(customer);
        redemption.setReward(reward);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(primaryService));
        when(redemptionRepository.findByIdAndCustomerAndStatus(10L, customer, RewardRedemptionStatus.AVAILABLE))
                .thenReturn(java.util.Optional.of(redemption));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookingService.create(requestAt(date.atTime(16, 0), 10L))).isNotNull();
        assertThatThrownBy(() -> bookingService.create(requestAt(date.atTime(16, 1), 10L)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking duration must end by 17:00");
    }

    @Test
    void statusAllowsEachRequiredProgressTransition() {
        assertAllowedTransition(BookingStatus.PENDING, BookingStatus.CONFIRMED);
        assertAllowedTransition(BookingStatus.CONFIRMED, BookingStatus.IN_QUEUE);
        assertAllowedTransition(BookingStatus.IN_QUEUE, BookingStatus.IN_PROGRESS);
    }

    @Test
    void statusAllowsCancellationFromEveryNonTerminalStatus() {
        assertAllowedTransition(BookingStatus.PENDING, BookingStatus.CANCELLED);
        assertAllowedTransition(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);
        assertAllowedTransition(BookingStatus.IN_QUEUE, BookingStatus.CANCELLED);
        assertAllowedTransition(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED);
    }

    @Test
    void statusAllowsInProgressToCompletedAndPreservesCompletionSideEffects() {
        Booking booking = bookingWithStatus(BookingStatus.IN_PROGRESS);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        BookingDtos.BookingResponse response = bookingService.updateStatus(99L, BookingStatus.COMPLETED);

        assertThat(response.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getCompletedAt()).isNotNull();
        assertThat(booking.getEarnedPoints()).isEqualTo(1);
        verify(loyaltyService).earnPoints(
                customer, BigDecimal.valueOf(10000), 1, "Earned points from booking #99", booking);
    }

    @Test
    void statusRejectsSkippingIntermediateStates() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateStatus(99L, BookingStatus.IN_PROGRESS))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid booking status transition from PENDING to IN_PROGRESS");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void statusRejectsConfirmedToInProgressWithoutCheckIn() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateStatus(99L, BookingStatus.IN_PROGRESS))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid booking status transition from CONFIRMED to IN_PROGRESS");
        assertThat(booking.getCheckInAt()).isNull();
    }

    @Test
    void statusRejectsTransitionsOutOfTerminalStates() {
        assertInvalidTransition(BookingStatus.COMPLETED, BookingStatus.CONFIRMED);
        assertInvalidTransition(BookingStatus.CANCELLED, BookingStatus.CONFIRMED);
    }

    @Test
    void cancellationRestoresPromotionUsageAndANonExpiredRewardRedemption() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        Promotion promotion = new Promotion();
        promotion.setUsedCount(1);
        RewardRedemption redemption = usedRedemption(LocalDateTime.now().plusDays(1));
        booking.setPromotion(promotion);
        booking.setRewardRedemption(redemption);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        bookingService.updateStatus(99L, BookingStatus.CANCELLED);

        verify(promotionService).restoreUsage(promotion);
        assertThat(redemption.getStatus()).isEqualTo(RewardRedemptionStatus.AVAILABLE);
        assertThat(redemption.getUsedAt()).isNull();
    }

    @Test
    void cancellationRejectsPromotionRestorationWhenUsageIsAlreadyZero() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        Promotion promotion = new Promotion();
        promotion.setUsedCount(0);
        booking.setPromotion(promotion);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        doThrow(new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Promotion usage cannot be restored"))
                .when(promotionService)
                .restoreUsage(promotion);

        assertThatThrownBy(() -> bookingService.updateStatus(99L, BookingStatus.CANCELLED))
                .isInstanceOf(ApiException.class)
                .hasMessage("Promotion usage cannot be restored");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void cancellationExpiresAnExpiredRewardRedemption() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        RewardRedemption redemption = usedRedemption(LocalDateTime.now().minusSeconds(1));
        booking.setRewardRedemption(redemption);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        bookingService.updateStatus(99L, BookingStatus.CANCELLED);

        assertThat(redemption.getStatus()).isEqualTo(RewardRedemptionStatus.EXPIRED);
        assertThat(redemption.getUsedAt()).isNotNull();
    }

    @Test
    void cancellationWithoutPromotionOrRewardSucceeds() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        BookingDtos.BookingResponse response = bookingService.updateStatus(99L, BookingStatus.CANCELLED);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void statusTransitionToInQueueRecordsCheckInTime() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        LocalDateTime before = LocalDateTime.now();

        BookingDtos.BookingResponse response = bookingService.updateStatus(99L, BookingStatus.IN_QUEUE);

        assertThat(response.status()).isEqualTo(BookingStatus.IN_QUEUE);
        assertThat(booking.getCheckInAt()).isAfterOrEqualTo(before);
    }

    @Test
    void appointmentResponseExposesInQueueStatus() {
        Booking booking = bookingWithStatus(BookingStatus.IN_QUEUE);

        BookingDtos.AppointmentResponse response = BookingDtos.AppointmentResponse.from(booking);

        assertThat(response.status()).isEqualTo("in_queue");
    }

    @Test
    void appointmentSummaryCountsQueueSeparatelyFromInProgress() {
        BookingDtos.AppointmentStatusSummary summary = BookingDtos.AppointmentStatusSummary.from(List.of(
                bookingWithStatus(BookingStatus.CONFIRMED),
                bookingWithStatus(BookingStatus.IN_QUEUE),
                bookingWithStatus(BookingStatus.IN_PROGRESS)));

        assertThat(summary.confirmed()).isEqualTo(1);
        assertThat(summary.inQueue()).isEqualTo(1);
        assertThat(summary.inProgress()).isEqualTo(1);
    }

    @Test
    void appointmentFiltersConfirmedAndQueueStatusesSeparately() {
        Booking confirmed = bookingWithStatus(BookingStatus.CONFIRMED);
        confirmed.setId(1L);
        Booking inQueue = bookingWithStatus(BookingStatus.IN_QUEUE);
        inQueue.setId(2L);
        when(bookingRepository.findAllByOrderByScheduledAtDesc()).thenReturn(List.of(confirmed, inQueue));

        BookingDtos.AppointmentPageResponse confirmedResponse = bookingService.allAppointments(
                new BookingDtos.AppointmentFilterParams(null, "confirmed", null, null, null, 1, 10, "scheduledAt", "asc"));
        BookingDtos.AppointmentPageResponse queueResponse = bookingService.allAppointments(
                new BookingDtos.AppointmentFilterParams(null, "in_queue", null, null, null, 1, 10, "scheduledAt", "asc"));

        assertThat(confirmedResponse.appointments()).extracting(BookingDtos.AppointmentResponse::status)
                .containsExactly("confirmed");
        assertThat(queueResponse.appointments()).extracting(BookingDtos.AppointmentResponse::status)
                .containsExactly("in_queue");
    }

    @Test
    void assignedStaffCanAdvanceThroughQueueLifecycleButCannotCancel() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        User staff = new User();
        staff.setId(7L);
        staff.setRole(UserRole.ROLE_STAFF);
        booking.setAssignedStaff(staff);
        when(authService.currentUser()).thenReturn(staff);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        bookingService.updateStatus(99L, BookingStatus.IN_QUEUE);
        LocalDateTime checkInAt = booking.getCheckInAt();
        bookingService.updateStatus(99L, BookingStatus.IN_PROGRESS);
        bookingService.updateStatus(99L, BookingStatus.COMPLETED);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getCheckInAt()).isEqualTo(checkInAt);

        Booking cancellable = bookingWithStatus(BookingStatus.CONFIRMED);
        cancellable.setAssignedStaff(staff);
        when(bookingRepository.findById(100L)).thenReturn(java.util.Optional.of(cancellable));
        assertThatThrownBy(() -> bookingService.updateStatus(100L, BookingStatus.CANCELLED))
                .isInstanceOf(ApiException.class)
                .hasMessage("You do not have permission to update this appointment status");
    }

    @Test
    void createPaymentStoresPendingPaymentMethodAndReturnsPaymentDetails() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        booking.setId(99L);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        BookingDtos.PaymentResponse response = bookingService.createPayment(
                99L, new BookingDtos.CreatePaymentRequest("cash"), "127.0.0.1");

        assertThat(booking.getPaymentMethod()).isEqualTo(BookingPaymentMethod.CASH);
        assertThat(booking.getPaymentStatus()).isEqualTo(BookingPaymentStatus.PENDING);
        assertThat(response.method()).isEqualTo("cash");
        assertThat(response.paymentUrl()).contains("/appointments/99/payment/confirm");
        assertThat(response.amount()).isEqualByComparingTo("10000");
    }

    @Test
    void updatePaymentStatusMarksAppointmentPaid() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        booking.setPaymentMethod(BookingPaymentMethod.MOMO);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        BookingDtos.AppointmentResponse response = bookingService.updatePaymentStatus(
                99L, new BookingDtos.UpdatePaymentStatusRequest("paid", null));

        assertThat(booking.getPaymentStatus()).isEqualTo(BookingPaymentStatus.PAID);
        assertThat(booking.getPaidAt()).isNotNull();
        assertThat(response.paymentStatus()).isEqualTo("paid");
        assertThat(response.paymentMethod()).isEqualTo("momo");
    }

    @Test
    void assignStaffStoresActiveStaffMember() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        User staff = new User();
        staff.setId(7L);
        staff.setFullName("Staff Member");
        staff.setPhone("0987654321");
        staff.setRole(UserRole.ROLE_STAFF);
        staff.setActive(true);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(staff));

        BookingDtos.AppointmentResponse response = bookingService.assignStaff(
                99L, new BookingDtos.AssignStaffRequest(7L));

        assertThat(booking.getAssignedStaff()).isEqualTo(staff);
        assertThat(((BookingDtos.AppointmentUser) response.assignedStaffId()).uid()).isEqualTo("7");
    }

    @Test
    void rescheduleUpdatesScheduledAtWhenSlotIsAvailable() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        booking.setScheduledAt(LocalDate.now().plusDays(1).atTime(9, 0));
        BookingService item = new BookingService();
        item.setDurationMinutes(30);
        booking.addService(item);
        LocalDateTime newSlot = LocalDate.now().plusDays(1).atTime(10, 0);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(booking));

        BookingDtos.AppointmentResponse response = bookingService.reschedule(
                99L, new BookingDtos.RescheduleRequest(newSlot));

        assertThat(booking.getScheduledAt()).isEqualTo(newSlot);
        assertThat(response.scheduledAt()).isEqualTo(newSlot);
    }

    @Test
    void rescheduleAllowsArbitraryMinuteStartWhenFree() {
        LocalDateTime newSlot = LocalDate.now().plusDays(1).atTime(9, 17);
        Booking booking = reschedulableBooking(LocalDate.now().plusDays(1).atTime(8, 0), 45);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(booking));

        bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(newSlot));

        assertThat(booking.getScheduledAt()).isEqualTo(newSlot);
    }

    @Test
    void rescheduleRejectsStartBeforeOpeningTime() {
        assertInvalidRescheduleStart(LocalDate.now().plusDays(1).atTime(7, 59));
    }

    @Test
    void rescheduleAllowsOpeningTime() {
        LocalDateTime newSlot = LocalDate.now().plusDays(1).atTime(8, 0);
        Booking booking = reschedulableBooking(LocalDate.now().plusDays(1).atTime(9, 0), 30);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(booking));

        bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(newSlot));

        assertThat(booking.getScheduledAt()).isEqualTo(newSlot);
    }

    @Test
    void rescheduleRejectsStartAtClosingTime() {
        assertInvalidRescheduleStart(LocalDate.now().plusDays(1).atTime(17, 0));
    }

    @Test
    void rescheduleRejectsStartWithSeconds() {
        assertInvalidRescheduleStart(LocalDate.now().plusDays(1).atTime(9, 17, 1));
    }

    @Test
    void rescheduleRejectsStartWithNanoseconds() {
        assertInvalidRescheduleStart(LocalDate.now().plusDays(1).atTime(9, 17).withNano(1));
    }

    @Test
    void rescheduleRejectsStartLessThanThirtyMinutesInAdvance() {
        LocalDateTime newSlot = LocalDateTime.now().plusMinutes(29).withSecond(0).withNano(0);
        Booking booking = reschedulableBooking(LocalDate.now().plusDays(1).atTime(9, 0), 30);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(newSlot)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking must be scheduled at least 30 minutes in advance");
    }

    @Test
    void rescheduleUsesExactPersistedDurationAtClosingBoundary() {
        LocalDate date = LocalDate.now().plusDays(1);
        Booking booking = reschedulableBooking(date.atTime(9, 0), 45);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(booking));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(booking));

        bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(date.atTime(16, 15)));
        assertThat(booking.getScheduledAt()).isEqualTo(date.atTime(16, 15));

        assertThatThrownBy(() -> bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(date.atTime(16, 16))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking duration must end by 17:00");
    }

    @Test
    void rescheduleRejectsOverlapAndAllowsBoundaryTouch() {
        LocalDate date = LocalDate.now().plusDays(1);
        Booking booking = reschedulableBooking(date.atTime(8, 0), 30);
        Booking existing = bookingAt(date, LocalTime.of(9, 0), BookingStatus.CONFIRMED, 45);
        existing.setId(100L);
        existing.setVehicle(booking.getVehicle());
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(booking.getVehicle(), List.of(
                        BookingStatus.PENDING,
                        BookingStatus.CONFIRMED,
                        BookingStatus.IN_QUEUE,
                        BookingStatus.IN_PROGRESS)))
                .thenReturn(List.of(booking, existing));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(booking, existing));

        assertThatThrownBy(() -> bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(date.atTime(9, 44))))
                .isInstanceOf(ApiException.class)
                .hasMessage("This vehicle already has an appointment in that time range");

        bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(date.atTime(9, 45)));
        assertThat(booking.getScheduledAt()).isEqualTo(date.atTime(9, 45));
    }

    @Test
    void rescheduleExcludesTheCurrentBookingFromOverlapChecks() {
        LocalDateTime newSlot = LocalDate.now().plusDays(1).atTime(10, 0);
        Booking booking = reschedulableBooking(newSlot, 30);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        when(bookingRepository.findByVehicleAndStatusInOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(booking));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(booking));

        bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(newSlot));

        assertThat(booking.getScheduledAt()).isEqualTo(newSlot);
    }

    @Test
    void rescheduleDoesNotApplyMembershipBookingWindow() {
        LocalDateTime newSlot = LocalDate.now().plusDays(8).atTime(9, 0);
        Booking booking = reschedulableBooking(LocalDate.now().plusDays(1).atTime(9, 0), 30);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(booking));

        bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(newSlot));

        assertThat(booking.getScheduledAt()).isEqualTo(newSlot);
    }

    @Test
    void subsequentTransitionToInProgressPreservesCheckInTime() {
        Booking booking = bookingWithStatus(BookingStatus.IN_QUEUE);
        LocalDateTime checkInAt = LocalDateTime.now().minusMinutes(10);
        booking.setCheckInAt(checkInAt);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        bookingService.updateStatus(99L, BookingStatus.IN_PROGRESS);

        assertThat(booking.getCheckInAt()).isEqualTo(checkInAt);
    }

    @Test
    void repeatedCheckInIsRejectedWithoutResettingCheckInTime() {
        Booking booking = bookingWithStatus(BookingStatus.IN_QUEUE);
        LocalDateTime checkInAt = LocalDateTime.now().minusMinutes(10);
        booking.setCheckInAt(checkInAt);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateStatus(99L, BookingStatus.IN_QUEUE))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid booking status transition from IN_QUEUE to IN_QUEUE");
        assertThat(booking.getCheckInAt()).isEqualTo(checkInAt);
    }

    @Test
    void priorityQueueExcludesConfirmedAndTerminalBookings() {
        Booking confirmed = queueBooking(1L, BookingStatus.CONFIRMED, 1, null, 30);
        Booking completed = queueBooking(2L, BookingStatus.COMPLETED, 1, null, 30);
        Booking cancelled = queueBooking(3L, BookingStatus.CANCELLED, 1, null, 30);
        Booking inQueue = queueBooking(4L, BookingStatus.IN_QUEUE, 1, LocalDateTime.now().minusMinutes(5), 30);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(confirmed, completed, cancelled, inQueue));

        List<BookingDtos.QueueItemResponse> queue = bookingService.priorityQueue();

        assertThat(queue).extracting(BookingDtos.QueueItemResponse::bookingId).containsExactly(4L);
    }

    @Test
    void priorityQueueRanksHigherMembershipPriorityFirst() {
        Booking lowerPriority = queueBooking(1L, BookingStatus.IN_QUEUE, 1, LocalDateTime.now().minusMinutes(30), 30);
        Booking higherPriority = queueBooking(2L, BookingStatus.IN_QUEUE, 2, LocalDateTime.now().minusMinutes(5), 30);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(lowerPriority, higherPriority));

        List<BookingDtos.QueueItemResponse> queue = bookingService.priorityQueue();

        assertThat(queue).extracting(BookingDtos.QueueItemResponse::bookingId).containsExactly(2L, 1L);
    }

    @Test
    void priorityQueueRanksTimestampedCheckInsBeforeLegacyEntries() {
        Booking legacy = queueBooking(1L, BookingStatus.IN_QUEUE, 1, null, 30);
        Booking timestamped = queueBooking(2L, BookingStatus.IN_QUEUE, 1, LocalDateTime.now().minusMinutes(1), 90);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any())).thenReturn(List.of(legacy, timestamped));

        List<BookingDtos.QueueItemResponse> queue = bookingService.priorityQueue();

        assertThat(queue).extracting(BookingDtos.QueueItemResponse::bookingId).containsExactly(2L, 1L);
        assertThat(queue.get(1).checkInAt()).isNull();
        assertThat(queue.get(1).waitingMinutes()).isNull();
        assertThat(queue.get(1).position()).isEqualTo(2);
    }

    @Test
    void priorityQueueRanksEarlierCheckInsBeforeLaterCheckIns() {
        LocalDateTime now = LocalDateTime.now();
        Booking laterCheckIn = queueBooking(1L, BookingStatus.IN_QUEUE, 1, now.minusMinutes(5), 30);
        Booking earlierCheckIn = queueBooking(2L, BookingStatus.IN_QUEUE, 1, now.minusMinutes(10), 90);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(laterCheckIn, earlierCheckIn));

        List<BookingDtos.QueueItemResponse> queue = bookingService.priorityQueue();

        assertThat(queue).extracting(BookingDtos.QueueItemResponse::bookingId).containsExactly(2L, 1L);
    }

    @Test
    void priorityQueueRanksShorterDurationAfterSameTierAndCheckInTime() {
        LocalDateTime checkInAt = LocalDateTime.now().minusMinutes(10);
        Booking longerDuration = queueBooking(1L, BookingStatus.IN_QUEUE, 1, checkInAt, 60);
        Booking shorterDuration = queueBooking(2L, BookingStatus.IN_QUEUE, 1, checkInAt, 30);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(longerDuration, shorterDuration));

        List<BookingDtos.QueueItemResponse> queue = bookingService.priorityQueue();

        assertThat(queue).extracting(BookingDtos.QueueItemResponse::bookingId).containsExactly(2L, 1L);
    }

    @Test
    void legacyPriorityQueueEntriesUseSnapshotDurationThenBookingId() {
        Booking laterId = queueBooking(5L, BookingStatus.IN_QUEUE, 1, null, 30);
        Booking earlierId = queueBooking(4L, BookingStatus.IN_QUEUE, 1, null, 30);
        Booking longerDuration = queueBooking(3L, BookingStatus.IN_QUEUE, 1, null, 60);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(laterId, earlierId, longerDuration));

        List<BookingDtos.QueueItemResponse> queue = bookingService.priorityQueue();

        assertThat(queue).extracting(BookingDtos.QueueItemResponse::bookingId).containsExactly(4L, 5L, 3L);
    }

    @Test
    void priorityQueueUsesSummedBookingServiceDurationSnapshots() {
        Booking booking = queueBooking(1L, BookingStatus.IN_QUEUE, 1, LocalDateTime.now().minusMinutes(5), 30, 45);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any())).thenReturn(List.of(booking));

        BookingDtos.QueueItemResponse item = bookingService.priorityQueue().get(0);

        assertThat(item.serviceDurationMinutes()).isEqualTo(75);
    }

    @Test
    void priorityQueueUsesZeroPriorityForCustomersWithoutMembership() {
        Booking booking = queueBooking(1L, BookingStatus.IN_QUEUE, 0, LocalDateTime.now().minusMinutes(5), 30);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any())).thenReturn(List.of(booking));

        BookingDtos.QueueItemResponse item = bookingService.priorityQueue().get(0);

        assertThat(item.priorityLevel()).isZero();
        assertThat(item.tierName()).isEqualTo("Member");
    }

    @Test
    void inProgressBookingsAreReturnedBeforeWaitingBookingsWithoutQueuePositions() {
        Booking lowerPriorityInProgress =
                queueBooking(5L, BookingStatus.IN_PROGRESS, 1, LocalDateTime.now().minusMinutes(10), 30);
        Booking higherPriorityInProgress =
                queueBooking(3L, BookingStatus.IN_PROGRESS, 3, LocalDateTime.now().minusMinutes(20), 30);
        Booking waiting = queueBooking(1L, BookingStatus.IN_QUEUE, 3, LocalDateTime.now().minusMinutes(30), 30);
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(lowerPriorityInProgress, higherPriorityInProgress, waiting));

        List<BookingDtos.QueueItemResponse> queue = bookingService.priorityQueue();

        assertThat(queue).extracting(BookingDtos.QueueItemResponse::bookingId).containsExactly(3L, 5L, 1L);
        assertThat(queue.get(0).position()).isNull();
        assertThat(queue.get(0).waitingMinutes()).isNull();
        assertThat(queue.get(2).position()).isEqualTo(1);
    }

    @Test
    void legacyQueueEntryNeverUsesScheduledAtAsWaitingTimeFallback() {
        Booking legacy = queueBooking(1L, BookingStatus.IN_QUEUE, 1, null, 30);
        legacy.setScheduledAt(LocalDateTime.now().minusDays(3));
        when(bookingRepository.findByStatusInOrderByScheduledAtAsc(any())).thenReturn(List.of(legacy));

        BookingDtos.QueueItemResponse item = bookingService.priorityQueue().get(0);

        assertThat(item.checkInAt()).isNull();
        assertThat(item.waitingMinutes()).isNull();
    }

    @Test
    void createUsesSerializableTransactionIsolation() throws NoSuchMethodException {
        Transactional transaction = BookingServiceLayer.class
                .getMethod("create", BookingDtos.CreateBookingRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.isolation()).isEqualTo(Isolation.SERIALIZABLE);
    }

    private BookingDtos.CreateBookingRequest requestAt(LocalDateTime scheduledAt) {
        return new BookingDtos.CreateBookingRequest(1L, List.of(1L), scheduledAt, null, null, null);
    }

    private BookingDtos.CreateBookingRequest requestAt(LocalDateTime scheduledAt, List<Long> serviceIds) {
        return new BookingDtos.CreateBookingRequest(1L, serviceIds, scheduledAt, null, null, null);
    }

    private BookingDtos.CreateBookingRequest requestAt(LocalDateTime scheduledAt, Long rewardRedemptionId) {
        return new BookingDtos.CreateBookingRequest(1L, List.of(1L), scheduledAt, null, rewardRedemptionId, null);
    }

    private void assertInvalidRescheduleStart(LocalDateTime newSlot) {
        Booking booking = reschedulableBooking(LocalDate.now().plusDays(1).atTime(9, 0), 30);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> bookingService.reschedule(99L, new BookingDtos.RescheduleRequest(newSlot)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking start time must be between 08:00 and 17:00 with minute precision");
    }

    private Booking reschedulableBooking(LocalDateTime scheduledAt, int durationMinutes) {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        booking.setScheduledAt(scheduledAt);
        BookingService item = new BookingService();
        item.setDurationMinutes(durationMinutes);
        booking.addService(item);
        return booking;
    }

    private void assertBookingEndTime(LocalTime startTime, int durationMinutes, boolean accepted) {
        LocalDateTime scheduledAt = LocalDate.now().plusDays(1).atTime(startTime);
        when(vehicleRepository.findByIdAndCustomer(1L, customer)).thenReturn(java.util.Optional.of(new Vehicle()));
        when(serviceRepository.findAllById(List.of(1L))).thenReturn(List.of(serviceWithDuration(durationMinutes)));
        when(bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        if (accepted) {
            assertThat(bookingService.create(requestAt(scheduledAt))).isNotNull();
            return;
        }
        assertThatThrownBy(() -> bookingService.create(requestAt(scheduledAt)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Booking duration must end by 17:00");
    }

    private Booking bookingAt(LocalDate date, LocalTime time, BookingStatus status) {
        Booking booking = new Booking();
        booking.setScheduledAt(date.atTime(time));
        booking.setStatus(status);
        return booking;
    }

    private Booking bookingAt(LocalDate date, LocalTime time, BookingStatus status, int durationMinutes) {
        Booking booking = bookingAt(date, time, status);
        BookingService bookingService = new BookingService();
        bookingService.setDurationMinutes(durationMinutes);
        booking.addService(bookingService);
        return booking;
    }

    private CarWashService serviceWithDuration(int durationMinutes) {
        CarWashService service = new CarWashService();
        ServiceCategory category = new ServiceCategory();
        category.setId((long) durationMinutes);
        category.setName("Category " + durationMinutes);
        service.setCategory(category);
        service.setDurationMinutes(durationMinutes);
        service.setPrice(BigDecimal.TEN);
        service.setActive(true);
        return service;
    }

    private void assertAllowedTransition(BookingStatus currentStatus, BookingStatus requestedStatus) {
        Booking booking = bookingWithStatus(currentStatus);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        BookingDtos.BookingResponse response = bookingService.updateStatus(99L, requestedStatus);

        assertThat(response.status()).isEqualTo(requestedStatus);
    }

    private void assertInvalidTransition(BookingStatus currentStatus, BookingStatus requestedStatus) {
        Booking booking = bookingWithStatus(currentStatus);
        when(bookingRepository.findById(99L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateStatus(99L, requestedStatus))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid booking status transition from " + currentStatus + " to " + requestedStatus);
        assertThat(booking.getStatus()).isEqualTo(currentStatus);
    }

    private Booking bookingWithStatus(BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(99L);
        booking.setCustomer(customer);
        booking.setVehicle(new Vehicle());
        booking.setStatus(status);
        booking.setScheduledAt(LocalDateTime.now().minusMinutes(30));
        booking.setSubtotalAmount(BigDecimal.valueOf(10000));
        booking.setDiscountAmount(BigDecimal.ZERO);
        booking.setFinalAmount(BigDecimal.valueOf(10000));
        return booking;
    }

    private RewardRedemption usedRedemption(LocalDateTime expiresAt) {
        RewardRedemption redemption = new RewardRedemption();
        redemption.setStatus(RewardRedemptionStatus.USED);
        redemption.setUsedAt(LocalDateTime.now().minusMinutes(1));
        redemption.setExpiresAt(expiresAt);
        return redemption;
    }

    private Booking queueBooking(
            Long id, BookingStatus status, int priorityLevel, LocalDateTime checkInAt, int... durationMinutes) {
        User queueCustomer = new User();
        Vehicle queueVehicle = new Vehicle();
        Booking booking = new Booking();
        booking.setId(id);
        booking.setCustomer(queueCustomer);
        booking.setVehicle(queueVehicle);
        booking.setStatus(status);
        booking.setScheduledAt(LocalDateTime.now().plusDays(1).plusMinutes(id));
        booking.setCheckInAt(checkInAt);
        booking.setFinalAmount(BigDecimal.TEN);
        for (int duration : durationMinutes) {
            BookingService item = new BookingService();
            item.setDurationMinutes(duration);
            booking.addService(item);
        }

        LoyaltyAccount queueAccount = new LoyaltyAccount();
        if (priorityLevel > 0) {
            MembershipTier tier = new MembershipTier();
            tier.setName("Tier " + priorityLevel);
            tier.setPriorityLevel(priorityLevel);
            queueAccount.setMembershipTier(tier);
        }
        when(loyaltyService.getOrCreateAccount(queueCustomer)).thenReturn(queueAccount);
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
