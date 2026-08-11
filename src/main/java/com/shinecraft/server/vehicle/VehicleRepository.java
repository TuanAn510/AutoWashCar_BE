package com.shinecraft.server.vehicle;

import com.shinecraft.server.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByCustomerAndIsActiveTrue(User customer);

    List<Vehicle> findByCustomerOrderByIsActiveDescIdAsc(User customer);

    Optional<Vehicle> findByIdAndCustomer(Long id, User customer);

    boolean existsByLicensePlate(String licensePlate);

    boolean existsByLicensePlateAndIsActiveTrue(String licensePlate);
}
