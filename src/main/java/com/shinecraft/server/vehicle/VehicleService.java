package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
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
        String plate = normalizePlate(request.licensePlate());
        if (vehicleRepository.existsByLicensePlate(plate)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bien so xe da ton tai");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(authService.currentUser());
        vehicle.setLicensePlate(plate);
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setColor(request.color());
        vehicle.setManufactureYear(request.manufactureYear());
        return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    private String normalizePlate(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase();
    }
}
