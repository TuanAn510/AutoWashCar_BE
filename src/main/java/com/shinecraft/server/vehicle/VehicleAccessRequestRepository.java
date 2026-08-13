package com.shinecraft.server.vehicle;

import com.shinecraft.server.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleAccessRequestRepository extends JpaRepository<VehicleAccessRequest, Long> {
    @EntityGraph(attributePaths = {"requester", "vehicle", "vehicle.customer", "brandRef", "modelRef"})
    List<VehicleAccessRequest> findByRequesterOrderByCreatedAtDesc(User requester);

    @EntityGraph(attributePaths = {"requester", "vehicle", "vehicle.customer", "brandRef", "modelRef"})
    List<VehicleAccessRequest> findByStatusOrderByCreatedAtDesc(VehicleAccessRequestStatus status);

    @EntityGraph(attributePaths = {"requester", "vehicle", "vehicle.customer", "brandRef", "modelRef"})
    List<VehicleAccessRequest> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"requester", "vehicle", "vehicle.customer", "brandRef", "modelRef"})
    Optional<VehicleAccessRequest> findWithDetailsById(Long id);

    boolean existsByVehicleIdAndRequestTypeAndStatus(
            Long vehicleId, VehicleAccessRequestType requestType, VehicleAccessRequestStatus status);

    List<VehicleAccessRequest> findByVehicleIdAndStatus(Long vehicleId, VehicleAccessRequestStatus status);
}
