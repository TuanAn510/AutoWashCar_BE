package com.shinecraft.server.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarWashServiceRepository extends JpaRepository<CarWashService, Long> {
    List<CarWashService> findByIsActiveTrueOrderByNameAsc();

    List<CarWashService> findByIsActiveTrueAndCategory_IsActiveTrueOrderByNameAsc();

    List<CarWashService> findByCategoryAndIsActiveTrue(ServiceCategory category);

    boolean existsByCategoryAndNameIgnoreCase(ServiceCategory category, String name);

    boolean existsByCategoryAndNameIgnoreCaseAndIdNot(ServiceCategory category, String name, Long id);
}
