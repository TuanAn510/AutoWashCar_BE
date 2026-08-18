package com.shinecraft.server.booking;

import com.shinecraft.server.user.User;
import com.shinecraft.server.vehicle.Vehicle;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @EntityGraph(attributePaths = {"customer", "vehicle", "assignedStaff", "secondaryAssignedStaff", "services"})
    List<Booking> findAllByOrderByScheduledAtDesc();

    @EntityGraph(attributePaths = {"customer", "vehicle", "services"})
    List<Booking> findByCustomerOrderByScheduledAtDesc(User customer);

    @EntityGraph(attributePaths = {"customer", "vehicle", "services"})
    List<Booking> findByCustomerOrderByCreatedAtDesc(User customer);

    @EntityGraph(attributePaths = {"customer", "vehicle", "assignedStaff", "secondaryAssignedStaff", "services"})
    List<Booking> findByAssignedStaffOrderByScheduledAtDesc(User assignedStaff);

    @EntityGraph(attributePaths = {"customer", "vehicle", "assignedStaff", "secondaryAssignedStaff", "services"})
    List<Booking> findByAssignedStaffOrSecondaryAssignedStaffOrderByScheduledAtDesc(
            User assignedStaff, User secondaryAssignedStaff);

    @EntityGraph(attributePaths = {"customer", "vehicle", "services"})
    List<Booking> findByScheduledAtBetweenOrderByScheduledAtAsc(LocalDateTime start, LocalDateTime end);

    @EntityGraph(attributePaths = {"customer", "vehicle", "services"})
    List<Booking> findByVehicleAndStatusInOrderByScheduledAtAsc(Vehicle vehicle, List<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"customer", "vehicle", "services"})
    List<Booking> findByStatusInOrderByScheduledAtAsc(List<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"customer", "vehicle", "services", "promotion", "rewardRedemption"})
    List<Booking> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            BookingStatus status, LocalDateTime scheduledAt);

    @EntityGraph(attributePaths = {"assignedStaff", "secondaryAssignedStaff"})
    List<Booking> findByAssignedStaffIsNotNull();

    @EntityGraph(attributePaths = {"assignedStaff", "secondaryAssignedStaff"})
    List<Booking> findByAssignedStaffIsNotNullOrSecondaryAssignedStaffIsNotNull();

}
