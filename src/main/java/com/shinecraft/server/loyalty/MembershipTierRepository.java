package com.shinecraft.server.loyalty;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipTierRepository extends JpaRepository<MembershipTier, Long> {
    List<MembershipTier> findByIsActiveTrueOrderByMinPointsAsc();

    Optional<MembershipTier> findFirstByIsActiveTrueAndMinPointsLessThanEqualOrderByMinPointsDesc(Integer points);
}
