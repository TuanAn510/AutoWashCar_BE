package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        return ApiResponse.ok("Vehicles retrieved successfully", vehicleService.myVehicles());
    }

    @GetMapping("/me")
    ApiResponse<List<VehicleDtos.VehicleResponse>> myVehiclesForFrontend() {
        return ApiResponse.ok("Vehicles retrieved successfully", vehicleService.myVehicles());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<VehicleDtos.VehicleResponse> create(@Valid @RequestBody VehicleDtos.VehicleRequest request) {
        return ApiResponse.ok("Vehicle created successfully", vehicleService.create(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<VehicleDtos.VehicleResponse> createForm(@ModelAttribute VehicleDtos.VehicleFormRequest request) {
        return ApiResponse.ok("Vehicle created successfully", vehicleService.create(request.toRequest()));
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<VehicleDtos.VehicleResponse> update(
            @PathVariable Long id, @Valid @RequestBody VehicleDtos.VehicleRequest request) {
        return ApiResponse.ok("Vehicle updated successfully", vehicleService.update(id, request));
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<VehicleDtos.VehicleResponse> updateForm(
            @PathVariable Long id, @ModelAttribute VehicleDtos.VehicleFormRequest request) {
        return ApiResponse.ok("Vehicle updated successfully", vehicleService.update(id, request.toRequest()));
    }

    @DeleteMapping("/{id}")
    ApiResponse<VehicleDtos.VehicleResponse> delete(@PathVariable Long id) {
        return ApiResponse.ok("Vehicle deleted successfully", vehicleService.delete(id));
    }
}
