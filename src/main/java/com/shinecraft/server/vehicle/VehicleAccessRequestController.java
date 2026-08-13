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
    private final VehicleBrandRepository brandRepository;
    private final VehicleModelRepository modelRepository;

    public VehicleAccessRequestController(
            AuthService authService,
            VehicleAccessRequestRepository requestRepository,
            VehicleRepository vehicleRepository,
            VehicleBrandRepository brandRepository,
            VehicleModelRepository modelRepository) {
        this.authService = authService;
        this.requestRepository = requestRepository;
        this.vehicleRepository = vehicleRepository;
        this.brandRepository = brandRepository;
        this.modelRepository = modelRepository;
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

        if (accessRequest.getRequestType() == VehicleAccessRequestType.BRAND_MODEL_VERIFICATION) {
            reviewBrandModelVerification(accessRequest, status);
        } else if (status == VehicleAccessRequestStatus.APPROVED && accessRequest.getVehicle() != null) {
            Vehicle vehicle = accessRequest.getVehicle();
            vehicle.setCustomer(accessRequest.getRequester());
            vehicle.setOwnershipStartAt(LocalDateTime.now());
            vehicleRepository.save(vehicle);
        }

        return VehicleAccessRequestResponse.from(requestRepository.save(accessRequest));
    }

    private void reviewBrandModelVerification(VehicleAccessRequest accessRequest, VehicleAccessRequestStatus status) {
        Vehicle vehicle = accessRequest.getVehicle();
        if (vehicle == null) {
            return;
        }
        User reviewer = authService.currentUser();
        if (status == VehicleAccessRequestStatus.APPROVED) {
            VehicleBrand brand = resolveOrCreateBrand(accessRequest, vehicle);
            VehicleModel model = resolveOrCreateModel(accessRequest, vehicle, brand);
            if (brand != null) {
                vehicle.setBrandRef(brand);
                vehicle.setBrand(brand.getName());
            }
            if (model != null) {
                vehicle.setModelRef(model);
                vehicle.setModel(model.getName());
            }
            vehicle.setVerificationStatus(VehicleVerificationStatus.APPROVED);
            vehicle.setVerifiedAt(LocalDateTime.now());
            vehicle.setVerifiedBy(reviewer.getId());
            vehicle.setVerificationNote(accessRequest.getReviewNote());
        } else {
            vehicle.setVerificationStatus(VehicleVerificationStatus.REJECTED);
            vehicle.setVerifiedAt(LocalDateTime.now());
            vehicle.setVerifiedBy(reviewer.getId());
            vehicle.setVerificationNote(accessRequest.getReviewNote());
        }
        vehicleRepository.save(vehicle);
    }

    private VehicleBrand resolveOrCreateBrand(VehicleAccessRequest accessRequest, Vehicle vehicle) {
        if (accessRequest.getBrandRef() != null) {
            return accessRequest.getBrandRef();
        }
        String name = hasText(accessRequest.getSuggestedBrandName())
                ? accessRequest.getSuggestedBrandName().trim()
                : (hasText(vehicle.getBrand()) ? vehicle.getBrand().trim() : null);
        if (name == null || isOther(name)) {
            return null;
        }
        return brandRepository
                .findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    VehicleBrand created = new VehicleBrand();
                    created.setName(name);
                    return brandRepository.save(created);
                });
    }

    private VehicleModel resolveOrCreateModel(VehicleAccessRequest accessRequest, Vehicle vehicle, VehicleBrand brand) {
        if (accessRequest.getModelRef() != null) {
            return accessRequest.getModelRef();
        }
        String name = hasText(accessRequest.getSuggestedModelName())
                ? accessRequest.getSuggestedModelName().trim()
                : (hasText(vehicle.getModel()) ? vehicle.getModel().trim() : null);
        if (name == null || isOther(name)) {
            return null;
        }
        if (brand != null) {
            return modelRepository
                    .findByBrandIdAndNameIgnoreCase(brand.getId(), name)
                    .orElseGet(() -> {
                        VehicleModel created = new VehicleModel();
                        created.setBrand(brand);
                        created.setName(name);
                        created.setNew(true);
                        return modelRepository.save(created);
                    });
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isOther(String value) {
        return value != null && value.trim().equalsIgnoreCase("OTHER");
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
            String requestType,
            String reviewNote,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt,
            RequesterSummary requesterId,
            VehicleDtos.VehicleResponse vehicleId,
            List<Object> documents,
            String suggestedBrandName,
            String suggestedModelName,
            Long brandId,
            Long modelId) {
        public static VehicleAccessRequestResponse from(VehicleAccessRequest request) {
            return new VehicleAccessRequestResponse(
                    String.valueOf(request.getId()),
                    request.getLicensePlate(),
                    request.getRelationship(),
                    request.getNote(),
                    request.getStatus().name().toLowerCase(),
                    request.getRequestType() == null
                            ? "access_request"
                            : request.getRequestType().name().toLowerCase(),
                    request.getReviewNote(),
                    request.getCreatedAt(),
                    request.getReviewedAt(),
                    new RequesterSummary(
                            String.valueOf(request.getRequester().getId()),
                            request.getRequester().getFullName(),
                            request.getRequester().getPhone()),
                    request.getVehicle() == null ? null : VehicleDtos.VehicleResponse.from(request.getVehicle()),
                    List.of(),
                    request.getSuggestedBrandName(),
                    request.getSuggestedModelName(),
                    request.getBrandRef() == null ? null : request.getBrandRef().getId(),
                    request.getModelRef() == null ? null : request.getModelRef().getId());
        }
    }
}
