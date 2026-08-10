package com.shinecraft.server.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarWashServiceRepository extends JpaRepository<CarWashService, Long> {
    List<CarWashService> findByIsActiveTrueOrderByNameAsc();
}
