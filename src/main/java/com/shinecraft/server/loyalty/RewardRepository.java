package com.shinecraft.server.loyalty;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRepository extends JpaRepository<Reward, Long> {
    List<Reward> findByIsActiveTrueOrderByRequiredPointsAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reward from Reward reward where reward.id = :id")
    Optional<Reward> findByIdForUpdate(@Param("id") Long id);
}
