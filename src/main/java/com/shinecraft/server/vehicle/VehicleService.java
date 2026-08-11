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
        String plate = LicensePlateNormalizer.normalize(request.licensePlate());
        if (vehicleRepository.existsByLicensePlateAndIsActiveTrue(plate)) {
            throw new ApiException(HttpStatus.CONFLICT, "License plate already exists");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(authService.currentUser());
        vehicle.setLicensePlate(plate);
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setColor(request.color());
        vehicle.setManufactureYear(request.manufactureYear());
        vehicle.setOwnershipStartAt(LocalDateTime.now());
        return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
    }
}
