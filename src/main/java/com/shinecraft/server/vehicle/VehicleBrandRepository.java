package com.shinecraft.server.vehicle;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleBrandRepository extends JpaRepository<VehicleBrand, Long> {
    @EntityGraph(attributePaths = {"models"})
    List<VehicleBrand> findByIsActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = {"models"})
    List<VehicleBrand> findAllByOrderByNameAsc();

    Optional<VehicleBrand> findByIdAndIsActiveTrue(Long id);

    Optional<VehicleBrand> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
