package com.shinecraft.server.booking;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {
    @EntityGraph(attributePaths = {"actor"})
    List<BookingStatusHistory> findByBookingOrderByChangedAtAscIdAsc(Booking booking);
}
