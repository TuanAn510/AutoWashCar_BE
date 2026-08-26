package com.shinecraft.server.vehicle;

import com.shinecraft.server.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByCustomerAndIsActiveTrue(User customer);

    List<Vehicle> findByCustomerOrderByIsActiveDescIdAsc(User customer);

    Optional<Vehicle> findByIdAndCustomer(Long id, User customer);

    Optional<Vehicle> findByLicensePlateAndIsActiveTrue(String licensePlate);

    /** Mọi xe ACTIVE đang giữ biển số (tối đa 1 do filtered unique index), dùng
     *  khi cần KHÓA xe cũ và gán chủ mới cho biển số. */
    List<Vehicle> findAllByLicensePlateAndIsActiveTrue(String licensePlate);

    boolean existsByLicensePlate(String licensePlate);

    boolean existsByLicensePlateAndIsActiveTrue(String licensePlate);

    boolean existsByLicensePlateAndIsActiveTrueAndIdNot(String licensePlate, Long id);
}
