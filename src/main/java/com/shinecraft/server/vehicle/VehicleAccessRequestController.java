package com.shinecraft.server.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shinecraft.server.common.ApiListResponse;
import com.shinecraft.server.common.ApiResponse;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.common.LicensePlateNormalizer;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VehicleAccessRequestController {
    private final AuthService authService;
    private final VehicleAccessRequestRepository requestRepository;
    private final VehicleRepository vehicleRepository;

    public VehicleAccessRequestController(
            AuthService authService,
            VehicleAccessRequestRepository requestRepository,
            VehicleRepository vehicleRepository) {
        this.authService = authService;
        this.requestRepository = requestRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @PostMapping(path = "/api/vehicle-access-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    ApiResponse<VehicleAccessRequestResponse> create(@ModelAttribute VehicleAccessRequestForm request) {
        User requester = authService.currentUser();
        String licensePlate = LicensePlateNormalizer.normalize(request.getLicensePlate());
        VehicleAccessRequest accessRequest = new VehicleAccessRequest();
        accessRequest.setRequester(requester);
        accessRequest.setLicensePlate(licensePlate);
        accessRequest.setRelationship(request.getRelationship());
        accessRequest.setNote(request.getNote());
        vehicleRepository.findByLicensePlateAndIsActiveTrue(licensePlate).ifPresent(accessRequest::setVehicle);
        return ApiResponse.ok(
                "Vehicle access request created successfully",
                VehicleAccessRequestResponse.from(requestRepository.save(accessRequest)));
    }

    @GetMapping("/api/vehicle-access-requests/me")
    @Transactional(readOnly = true)
    ApiListResponse<VehicleAccessRequestResponse> mine() {
        return ApiListResponse.ok(
                "Vehicle access requests retrieved successfully",
                requestRepository.findByRequesterOrderByCreatedAtDesc(authService.currentUser()).stream()
                        .map(VehicleAccessRequestResponse::from)
                        .toList());
    }

    @GetMapping("/api/vehicle-access-requests")
    @Transactional(readOnly = true)
    ApiListResponse<VehicleAccessRequestResponse> all(@RequestParam(required = false) String status) {
        List<VehicleAccessRequest> requests = status == null || status.isBlank()
                ? requestRepository.findAllByOrderByCreatedAtDesc()
                : requestRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        return ApiListResponse.ok(
                "Vehicle access requests retrieved successfully",
                requests.stream()
                        .map(VehicleAccessRequestResponse::from)
                        .toList());
    }

    @PatchMapping("/api/vehicle-access-requests/{id}/approve")
    @Transactional
    ApiResponse<VehicleAccessRequestResponse> approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request) {
        return ApiResponse.ok("Vehicle access request approved successfully", review(id, VehicleAccessRequestStatus.APPROVED, request));
    }

    @PatchMapping("/api/vehicle-access-requests/{id}/reject")
    @Transactional
    ApiResponse<VehicleAccessRequestResponse> reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request) {
        return ApiResponse.ok("Vehicle access request rejected successfully", review(id, VehicleAccessRequestStatus.REJECTED, request));
    }

    private VehicleAccessRequestResponse review(Long id, VehicleAccessRequestStatus status, ReviewRequest request) {
        VehicleAccessRequest accessRequest = requestRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle access request not found"));
        accessRequest.setStatus(status);
        accessRequest.setReviewNote(request == null ? null : request.reviewNote());
        accessRequest.setReviewedAt(LocalDateTime.now());

        if (status == VehicleAccessRequestStatus.APPROVED && accessRequest.getVehicle() != null) {
            Vehicle vehicle = accessRequest.getVehicle();
            vehicle.setCustomer(accessRequest.getRequester());
            vehicle.setOwnershipStartAt(LocalDateTime.now());
            vehicleRepository.save(vehicle);
        }

        return VehicleAccessRequestResponse.from(requestRepository.save(accessRequest));
    }

    private VehicleAccessRequestStatus parseStatus(String status) {
        try {
            return VehicleAccessRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid vehicle access request status");
        }
    }

    public static class VehicleAccessRequestForm {
        private String licensePlate;
        private String relationship;
        private String note;

        public String getLicensePlate() {
            return licensePlate;
        }

        public void setLicensePlate(String licensePlate) {
            this.licensePlate = licensePlate;
        }

        public String getRelationship() {
            return relationship;
        }

        public void setRelationship(String relationship) {
            this.relationship = relationship;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    public record ReviewRequest(String reviewNote) {}

    public record RequesterSummary(
            @JsonProperty("_id") String uid,
            String displayName,
            String phone) {}

    public record VehicleAccessRequestResponse(
            @JsonProperty("_id") String uid,
            String licensePlate,
            String relationship,
            String note,
            String status,
            String reviewNote,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt,
            RequesterSummary requesterId,
            VehicleDtos.VehicleResponse vehicleId,
            List<Object> documents) {
        public static VehicleAccessRequestResponse from(VehicleAccessRequest request) {
            return new VehicleAccessRequestResponse(
                    String.valueOf(request.getId()),
                    request.getLicensePlate(),
                    request.getRelationship(),
                    request.getNote(),
                    request.getStatus().name().toLowerCase(),
                    request.getReviewNote(),
                    request.getCreatedAt(),
                    request.getReviewedAt(),
                    new RequesterSummary(
                            String.valueOf(request.getRequester().getId()),
                            request.getRequester().getFullName(),
                            request.getRequester().getPhone()),
                    request.getVehicle() == null ? null : VehicleDtos.VehicleResponse.from(request.getVehicle()),
                    List.of());
        }
    }
}
