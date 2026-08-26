package com.shinecraft.server.vehicle;

import com.shinecraft.server.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Tìm kiếm xe có phân trang + filter ở database, JOIN FETCH customer để
     *  tránh N+1. Dùng cho admin listAll(). */
    @Query(value = """
            SELECT v FROM Vehicle v JOIN FETCH v.customer c
            WHERE v.isActive = true
            AND (:carType IS NULL OR v.carType = :carType)
            AND (:keyword IS NULL OR :keyword = ''
                 OR LOWER(v.licensePlate) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(v.brand)       LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(v.model)       LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(c.fullName)    LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(c.phone)       LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
            countQuery = """
            SELECT COUNT(v) FROM Vehicle v JOIN v.customer c
            WHERE v.isActive = true
            AND (:carType IS NULL OR v.carType = :carType)
            AND (:keyword IS NULL OR :keyword = ''
                 OR LOWER(v.licensePlate) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(v.brand)       LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(v.model)       LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(c.fullName)    LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(c.phone)       LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Vehicle> findFiltered(
            @Param("keyword") String keyword,
            @Param("carType") String carType,
            Pageable pageable);
}