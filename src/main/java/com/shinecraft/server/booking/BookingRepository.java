package com.shinecraft.server.booking;

import com.shinecraft.server.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @EntityGraph(attributePaths = {"customer", "vehicle", "services"})
    List<Booking> findByCustomerOrderByScheduledAtDesc(User customer);

    @EntityGraph(attributePaths = {"customer", "vehicle", "services"})
    List<Booking> findByScheduledAtBetweenOrderByScheduledAtAsc(LocalDateTime start, LocalDateTime end);

    @EntityGraph(attributePaths = {"customer", "vehicle", "services"})
    List<Booking> findByStatusInOrderByScheduledAtAsc(List<BookingStatus> statuses);

    boolean existsByScheduledAtAndStatusIn(LocalDateTime scheduledAt, List<BookingStatus> statuses);
}
