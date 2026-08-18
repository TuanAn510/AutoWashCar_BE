package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.ApiListResponse;
import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    ApiResponse<List<VehicleDtos.VehicleResponse>> myVehicles(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok("Vehicles retrieved successfully", vehicleService.myVehicles(includeInactive));
    }

    @GetMapping("/me")
    ApiResponse<List<VehicleDtos.VehicleResponse>> myVehiclesForFrontend(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok("Vehicles retrieved successfully", vehicleService.myVehicles(includeInactive));
    }

    @GetMapping
    ApiListResponse<VehicleDtos.VehicleResponse> listAll(@RequestParam Map<String, String> params) {
        VehicleDtos.VehiclePage result = vehicleService.listAll(
                parseInt(params.get("page")),
                parseInt(params.get("limit")),
                params.get("keyword"),
                params.get("carType"),
                params.get("sortBy"),
                params.get("sortOrder"));
        return new ApiListResponse<>(true, "Vehicles retrieved successfully", result.vehicles(), result.pagination());
    }

    @GetMapping("/{id}")
    ApiResponse<VehicleDtos.VehicleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("Vehicle retrieved successfully", vehicleService.get(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<VehicleDtos.VehicleResponse> create(@Valid @RequestBody VehicleDtos.VehicleRequest request) {
        return ApiResponse.ok("Vehicle created successfully", vehicleService.create(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<VehicleDtos.VehicleResponse> createForm(@ModelAttribute VehicleDtos.VehicleFormRequest request) {
        return ApiResponse.ok("Vehicle created successfully",
                vehicleService.create(request.toRequest(), request.getFiles()));
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<VehicleDtos.VehicleResponse> update(
            @PathVariable Long id, @Valid @RequestBody VehicleDtos.VehicleRequest request) {
        return ApiResponse.ok("Vehicle updated successfully", vehicleService.update(id, request));
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<VehicleDtos.VehicleResponse> updateForm(
            @PathVariable Long id, @ModelAttribute VehicleDtos.VehicleFormRequest request) {
        return ApiResponse.ok("Vehicle updated successfully",
                vehicleService.update(id, request.toRequest(), request.getFiles()));
    }

    @DeleteMapping("/{id}")
    ApiResponse<VehicleDtos.VehicleResponse> delete(@PathVariable Long id) {
        return ApiResponse.ok("Vehicle deleted successfully", vehicleService.delete(id));
    }

    @PatchMapping("/{id}/dismiss")
    ApiResponse<VehicleDtos.VehicleResponse> dismiss(@PathVariable Long id) {
        return ApiResponse.ok("Vehicle dismissed successfully", vehicleService.dismiss(id));
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
