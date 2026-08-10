package com.shinecraft.server.promotion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCodeIgnoreCase(String code);

    List<Promotion> findByIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
            LocalDateTime startAt, LocalDateTime endAt);
}
