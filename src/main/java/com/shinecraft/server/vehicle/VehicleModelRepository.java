package com.shinecraft.server.vehicle;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {
    List<VehicleModel> findByBrandIdAndIsActiveTrueOrderByNameAsc(Long brandId);

    List<VehicleModel> findByBrandIdOrderByNameAsc(Long brandId);

    Optional<VehicleModel> findByIdAndIsActiveTrue(Long id);

    Optional<VehicleModel> findByBrandIdAndNameIgnoreCase(Long brandId, String name);

    boolean existsByBrandIdAndNameIgnoreCase(Long brandId, String name);
}
