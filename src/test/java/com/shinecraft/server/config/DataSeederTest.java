package com.shinecraft.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.booking.BookingPaymentStatus;
import com.shinecraft.server.booking.BookingRepository;
import com.shinecraft.server.booking.BookingStatus;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.catalog.ServiceCategoryRepository;
import com.shinecraft.server.loyalty.LoyaltyAccountRepository;
import com.shinecraft.server.loyalty.LoyaltyTransactionRepository;
import com.shinecraft.server.loyalty.MembershipTierRepository;
import com.shinecraft.server.loyalty.PointLotRepository;
import com.shinecraft.server.loyalty.RewardRepository;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleBrandRepository;
import com.shinecraft.server.vehicle.VehicleModelRepository;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataSeederTest {
    private static final String UPDATE_AUDIT_TIMESTAMPS =
            "UPDATE bookings SET created_at = ?, updated_at = ? WHERE id = ?";

    private BookingRepository bookingRepository;
    private JdbcTemplate jdbcTemplate;
    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        dataSeeder = new DataSeeder(
                mock(UserRepository.class),
                mock(MembershipTierRepository.class),
                mock(LoyaltyAccountRepository.class),
                mock(LoyaltyTransactionRepository.class),
                mock(PointLotRepository.class),
                mock(ServiceCategoryRepository.class),
                mock(CarWashServiceRepository.class),
                mock(RewardRepository.class),
                mock(VehicleRepository.class),
                mock(VehicleBrandRepository.class),
                mock(VehicleModelRepository.class),
                bookingRepository,
                jdbcTemplate,
                mock(PasswordEncoder.class),
                "0900000000",
                "admin-password",
                "0900000001",
                "staff-password",
                "0900000002",
                "0900000003",
                "0900000004",
                "customer-password",
                true);
    }

    @Test
    void correctsHistoricalDemoBookingWithDeterministicCreationTime() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 8, 12, 14, 41);
        Booking booking = booking(41L, scheduledAt, BookingStatus.CONFIRMED);

        dataSeeder.correctDemoBookingAuditTimestamps(booking);

        Object[] arguments = capturedUpdateArguments();
        LocalDateTime createdAt = ((Timestamp) arguments[0]).toLocalDateTime();
        LocalDateTime updatedAt = ((Timestamp) arguments[1]).toLocalDateTime();
        assertThat(createdAt).isEqualTo(scheduledAt.minusDays(2));
        assertThat(createdAt).isBefore(scheduledAt);
        assertThat(updatedAt).isEqualTo(createdAt);
        assertThat(arguments[2]).isEqualTo(41L);
    }

    @Test
    void completedDemoBookingUsesCompletedAtAsUpdatedAt() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 8, 12, 14, 41);
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 12, 16, 0);
        Booking booking = booking(42L, scheduledAt, BookingStatus.COMPLETED);
        booking.setPaidAt(completedAt);
        booking.setCompletedAt(completedAt);

        dataSeeder.correctDemoBookingAuditTimestamps(booking);

        Object[] arguments = capturedUpdateArguments();
        LocalDateTime createdAt = ((Timestamp) arguments[0]).toLocalDateTime();
        LocalDateTime updatedAt = ((Timestamp) arguments[1]).toLocalDateTime();
        assertThat(createdAt).isBefore(scheduledAt);
        assertThat(scheduledAt).isBeforeOrEqualTo(completedAt);
        assertThat(createdAt).isBeforeOrEqualTo(booking.getPaidAt());
        assertThat(updatedAt).isEqualTo(completedAt);
    }

    @Test
    void existingMarkerBookingIsCorrectedWithoutCreatingDuplicate() {
        Booking existing = booking(43L, LocalDateTime.of(2026, 8, 20, 10, 23), BookingStatus.IN_QUEUE);
        existing.setNote("demo-marker");
        when(bookingRepository.findAll()).thenReturn(List.of(existing));

        Booking result = dataSeeder.createDemoBookingIfMissing(
                "demo-marker",
                new User(),
                new Vehicle(),
                new User(),
                List.of(),
                existing.getScheduledAt(),
                BookingStatus.IN_QUEUE,
                BookingPaymentStatus.UNPAID,
                null);

        assertThat(result).isSameAs(existing);
        assertThat(existing.getCheckInAt()).isNull();
        verify(bookingRepository, never()).save(org.mockito.ArgumentMatchers.any(Booking.class));
        Object[] arguments = capturedUpdateArguments();
        assertThat(arguments[2]).isEqualTo(43L);
    }

    private Object[] capturedUpdateArguments() {
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(eq(UPDATE_AUDIT_TIMESTAMPS), arguments.capture());
        return arguments.getValue();
    }

    private Booking booking(Long id, LocalDateTime scheduledAt, BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setScheduledAt(scheduledAt);
        booking.setStatus(status);
        return booking;
    }
}
