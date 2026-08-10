package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vehicles")
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping("/my")
    ApiResponse<List<VehicleDtos.VehicleResponse>> myVehicles() {
        return ApiResponse.ok("Lay danh sach xe thanh cong", vehicleService.myVehicles());
    }

    @PostMapping
    ApiResponse<VehicleDtos.VehicleResponse> create(@Valid @RequestBody VehicleDtos.VehicleRequest request) {
        return ApiResponse.ok("Tao xe thanh cong", vehicleService.create(request));
    }
}
