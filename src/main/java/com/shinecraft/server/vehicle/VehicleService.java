package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.common.LicensePlateNormalizer;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final AuthService authService;

    public VehicleService(VehicleRepository vehicleRepository, AuthService authService) {
        this.vehicleRepository = vehicleRepository;
        this.authService = authService;
    }

    public List<VehicleDtos.VehicleResponse> myVehicles() {
        User customer = authService.currentUser();
        return vehicleRepository.findByCustomerAndIsActiveTrue(customer).stream()
                .map(VehicleDtos.VehicleResponse::from)
                .toList();
    }

    @Transactional
    public VehicleDtos.VehicleResponse create(VehicleDtos.VehicleRequest request) {
        requireForCreate(request);
        String plate = LicensePlateNormalizer.normalize(request.licensePlate());
        if (vehicleRepository.existsByLicensePlateAndIsActiveTrue(plate)) {
            throw new ApiException(HttpStatus.CONFLICT, "License plate already exists", "VEHICLE_VERIFICATION_REQUIRED");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(authService.currentUser());
        vehicle.setLicensePlate(plate);
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setColor(request.color());
        vehicle.setManufactureYear(request.resolvedYear());
        vehicle.setOwnershipStartAt(LocalDateTime.now());
        return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleDtos.VehicleResponse update(Long id, VehicleDtos.VehicleRequest request) {
        User customer = authService.currentUser();
        Vehicle vehicle = vehicleRepository
                .findByIdAndCustomer(id, customer)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (!vehicle.isActive()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }

        if (request.licensePlate() != null && !request.licensePlate().isBlank()) {
            String plate = LicensePlateNormalizer.normalize(request.licensePlate());
            vehicleRepository.findAll().stream()
                    .filter(existing -> existing.isActive())
                    .filter(existing -> !existing.getId().equals(id))
                    .filter(existing -> existing.getLicensePlate().equals(plate))
                    .findAny()
                    .ifPresent(existing -> {
                        throw new ApiException(HttpStatus.CONFLICT, "License plate already exists");
                    });
            vehicle.setLicensePlate(plate);
        }
        if (request.brand() != null && !request.brand().isBlank()) {
            vehicle.setBrand(request.brand().trim());
        }
        if (request.model() != null && !request.model().isBlank()) {
            vehicle.setModel(request.model().trim());
        }
        if (request.color() != null) {
            vehicle.setColor(request.color());
        }
        if (request.resolvedYear() != null) {
            vehicle.setManufactureYear(request.resolvedYear());
        }
        return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleDtos.VehicleResponse delete(Long id) {
        User customer = authService.currentUser();
        Vehicle vehicle = vehicleRepository
                .findByIdAndCustomer(id, customer)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        vehicle.setActive(false);
        vehicle.setOwnershipEndAt(LocalDateTime.now());
        return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    private void requireForCreate(VehicleDtos.VehicleRequest request) {
        if (request.licensePlate() == null || request.licensePlate().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "License plate is required");
        }
        if (request.brand() == null || request.brand().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Brand is required");
        }
        if (request.model() == null || request.model().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Model is required");
        }
    }
}
