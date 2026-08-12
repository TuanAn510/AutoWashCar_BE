package com.shinecraft.server.promotion;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCodeIgnoreCase(String code);

    List<Promotion> findByIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
            LocalDateTime startAt, LocalDateTime endAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select promotion from Promotion promotion where promotion.id = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") Long id);
}
