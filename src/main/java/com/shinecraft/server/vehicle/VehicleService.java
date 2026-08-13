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
    private final VehicleBrandRepository brandRepository;
    private final VehicleModelRepository modelRepository;
    private final VehicleAccessRequestRepository accessRequestRepository;

    public VehicleService(
            VehicleRepository vehicleRepository,
            AuthService authService,
            VehicleBrandRepository brandRepository,
            VehicleModelRepository modelRepository,
            VehicleAccessRequestRepository accessRequestRepository) {
        this.vehicleRepository = vehicleRepository;
        this.authService = authService;
        this.brandRepository = brandRepository;
        this.modelRepository = modelRepository;
        this.accessRequestRepository = accessRequestRepository;
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
        vehicle.setColor(request.color());
        vehicle.setManufactureYear(request.resolvedYear());
        vehicle.setOwnershipStartAt(LocalDateTime.now());

        ResolvedBrandModel resolved = resolveBrandModel(request);
        VehicleBrand brand = resolved.brand();
        VehicleModel model = resolved.model();

        if (resolved.catalogSelected()) {
            vehicle.setBrand(brand.getName());
            vehicle.setModel(model.getName());
            vehicle.setBrandRef(brand);
            vehicle.setModelRef(model);
            vehicle.setVerificationStatus(VehicleVerificationStatus.APPROVED);
            return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
        }

        if (resolved.legacyPlainText()) {
            vehicle.setBrand(trim(request.brand()));
            vehicle.setModel(trim(request.model()));
            vehicle.setVerificationStatus(VehicleVerificationStatus.APPROVED);
            return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
        }

        // OTHER or suggested custom brand/model present → PENDING + verification request
        String brandText = hasText(request.suggestedBrandName()) ? request.suggestedBrandName().trim() : trim(request.brand());
        String modelText = hasText(request.suggestedModelName()) ? request.suggestedModelName().trim() : trim(request.model());
        vehicle.setBrand(brandText);
        vehicle.setModel(modelText);
        if (brand != null) {
            vehicle.setBrandRef(brand);
        }
        if (model != null) {
            vehicle.setModelRef(model);
        }
        vehicle.setVerificationStatus(VehicleVerificationStatus.PENDING);
        Vehicle saved = vehicleRepository.save(vehicle);
        createVerificationRequest(saved, request, brand, model);
        return VehicleDtos.VehicleResponse.from(saved);
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

        boolean brandModelTouched = request.brand() != null
                || request.model() != null
                || request.resolvedBrandId() != null
                || request.resolvedModelId() != null
                || hasText(request.suggestedBrandName())
                || hasText(request.suggestedModelName());
        if (brandModelTouched) {
            applyBrandModelUpdate(vehicle, request);
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
        accessRequestRepository.findByVehicleIdAndStatus(vehicle.getId(), VehicleAccessRequestStatus.PENDING)
                .forEach(request -> {
                    request.setStatus(VehicleAccessRequestStatus.REJECTED);
                    request.setReviewNote("Vehicle was deleted by customer");
                    request.setReviewedAt(LocalDateTime.now());
                });
        return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    private void applyBrandModelUpdate(Vehicle vehicle, VehicleDtos.VehicleRequest request) {
        ResolvedBrandModel resolved = resolveBrandModel(request);
        VehicleBrand brand = resolved.brand();
        VehicleModel model = resolved.model();

        if (resolved.catalogSelected()) {
            vehicle.setBrand(brand.getName());
            vehicle.setModel(model.getName());
            vehicle.setBrandRef(brand);
            vehicle.setModelRef(model);
            vehicle.setVerificationStatus(VehicleVerificationStatus.APPROVED);
            return;
        }

        if (resolved.legacyPlainText()) {
            if (request.brand() != null && !request.brand().isBlank()) {
                vehicle.setBrand(request.brand().trim());
            }
            if (request.model() != null && !request.model().isBlank()) {
                vehicle.setModel(request.model().trim());
            }
            vehicle.setVerificationStatus(VehicleVerificationStatus.APPROVED);
            return;
        }

        if (request.brand() != null && !request.brand().isBlank()) {
            vehicle.setBrand(
                    hasText(request.suggestedBrandName()) ? request.suggestedBrandName().trim() : request.brand().trim());
        }
        if (request.model() != null && !request.model().isBlank()) {
            vehicle.setModel(
                    hasText(request.suggestedModelName()) ? request.suggestedModelName().trim() : request.model().trim());
        }
        if (brand != null) {
            vehicle.setBrandRef(brand);
        }
        if (model != null) {
            vehicle.setModelRef(model);
        }
        vehicle.setVerificationStatus(VehicleVerificationStatus.PENDING);
        boolean alreadyRequested = accessRequestRepository
                .existsByVehicleIdAndRequestTypeAndStatus(
                        vehicle.getId(), VehicleAccessRequestType.BRAND_MODEL_VERIFICATION, VehicleAccessRequestStatus.PENDING);
        if (!alreadyRequested) {
            createVerificationRequest(vehicle, request, brand, model);
        }
    }

    private ResolvedBrandModel resolveBrandModel(VehicleDtos.VehicleRequest request) {
        VehicleBrand brand = resolveBrand(request.resolvedBrandId());
        VehicleModel model = resolveModel(request.resolvedModelId(), brand);
        boolean suggestedBrand = hasText(request.suggestedBrandName());
        boolean suggestedModel = hasText(request.suggestedModelName());
        boolean otherBrand = isOther(request.brand());
        boolean otherModel = isOther(request.model());
        boolean hasCatalogSelection = request.resolvedBrandId() != null || request.resolvedModelId() != null;

        if (otherBrand && !suggestedBrand) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Custom brand name is required when brand is OTHER");
        }
        if (otherModel && !suggestedModel) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Custom model name is required when model is OTHER");
        }
        if (brand == null && model != null) {
            brand = model.getBrand();
        }
        boolean catalogSelected = brand != null
                && model != null
                && !otherBrand
                && !otherModel
                && !suggestedBrand
                && !suggestedModel;
        boolean legacyPlainText = !hasCatalogSelection
                && !otherBrand
                && !otherModel
                && !suggestedBrand
                && !suggestedModel;
        return new ResolvedBrandModel(brand, model, catalogSelected, legacyPlainText);
    }

    private record ResolvedBrandModel(
            VehicleBrand brand, VehicleModel model, boolean catalogSelected, boolean legacyPlainText) {}

    private void createVerificationRequest(
            Vehicle vehicle, VehicleDtos.VehicleRequest request, VehicleBrand brand, VehicleModel model) {
        VehicleAccessRequest accessRequest = new VehicleAccessRequest();
        accessRequest.setRequester(authService.currentUser());
        accessRequest.setVehicle(vehicle);
        accessRequest.setLicensePlate(vehicle.getLicensePlate());
        accessRequest.setRelationship("BRAND_MODEL_VERIFICATION");
        accessRequest.setNote("Vehicle brand/model verification request");
        accessRequest.setRequestType(VehicleAccessRequestType.BRAND_MODEL_VERIFICATION);
        accessRequest.setSuggestedBrandName(
                hasText(request.suggestedBrandName()) ? request.suggestedBrandName().trim() : trim(request.brand()));
        accessRequest.setSuggestedModelName(
                hasText(request.suggestedModelName()) ? request.suggestedModelName().trim() : trim(request.model()));
        accessRequest.setBrandRef(brand);
        accessRequest.setModelRef(model);
        accessRequestRepository.save(accessRequest);
    }

    private VehicleBrand resolveBrand(Long id) {
        if (id == null) {
            return null;
        }
        return brandRepository.findByIdAndIsActiveTrue(id).orElse(null);
    }

    private VehicleModel resolveModel(Long id, VehicleBrand brand) {
        if (id == null) {
            return null;
        }
        return modelRepository
                .findByIdAndIsActiveTrue(id)
                .filter(model -> brand == null || model.getBrand().getId().equals(brand.getId()))
                .orElse(null);
    }

    private boolean isOther(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        return normalized.equalsIgnoreCase("OTHER") || normalized.equalsIgnoreCase("__other__");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
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
