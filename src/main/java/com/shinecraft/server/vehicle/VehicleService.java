package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.common.FileStorageService;
import com.shinecraft.server.common.LicensePlateNormalizer;
import com.shinecraft.server.common.PaginationMeta;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRole;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final FileStorageService fileStorageService;
    private final AuthService authService;
    private final VehicleBrandRepository brandRepository;
    private final VehicleModelRepository modelRepository;
    private final VehicleAccessRequestRepository accessRequestRepository;
    private final TransactionTemplate requiresNewTx;

    public VehicleService(VehicleRepository vehicleRepository,
                           VehicleImageRepository vehicleImageRepository,
                           FileStorageService fileStorageService,
                           AuthService authService,
            VehicleBrandRepository brandRepository,
            VehicleModelRepository modelRepository,
            VehicleAccessRequestRepository accessRequestRepository,
            PlatformTransactionManager platformTransactionManager) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleImageRepository = vehicleImageRepository;
        this.fileStorageService = fileStorageService;
        this.authService = authService;
        this.brandRepository = brandRepository;
        this.modelRepository = modelRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.requiresNewTx = new TransactionTemplate(platformTransactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<VehicleDtos.VehicleResponse> myVehicles() {
        User customer = authService.currentUser();
        return vehicleRepository.findByCustomerAndIsActiveTrue(customer).stream()
                .map(VehicleDtos.VehicleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleDtos.VehiclePage listAll(
            Integer page,
            Integer limit,
            String keyword,
            String carType,
            String sortBy,
            String sortOrder) {
        requireAdmin();
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedCarType = normalizeOptionalCarType(carType);

        List<Vehicle> filtered = vehicleRepository.findAll().stream()
                .filter(Vehicle::isActive)
                .filter(vehicle -> normalizedCarType == null || normalizedCarType.equals(vehicle.getCarType()))
                .filter(vehicle -> matchesKeyword(vehicle, normalizedKeyword))
                .sorted(comparator(sortBy, sortOrder))
                .toList();

        long total = filtered.size();
        int from = Math.min((resolvedPage - 1) * resolvedLimit, filtered.size());
        int to = Math.min(from + resolvedLimit, filtered.size());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / resolvedLimit);

        return new VehicleDtos.VehiclePage(
                filtered.subList(from, to).stream().map(VehicleDtos.VehicleResponse::from).toList(),
                new PaginationMeta(resolvedPage, resolvedLimit, total, totalPages));
    }

    @Transactional(readOnly = true)
    public VehicleDtos.VehicleResponse get(Long id) {
        Vehicle vehicle = vehicleRepository
                .findById(id)
                .filter(Vehicle::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        requireAdminOrOwner(vehicle);
        return VehicleDtos.VehicleResponse.from(vehicle);
    }

    @Transactional
    public VehicleDtos.VehicleResponse create(VehicleDtos.VehicleRequest request) {
        return create(request, null);
    }

    @Transactional
    public VehicleDtos.VehicleResponse create(VehicleDtos.VehicleRequest request, List<MultipartFile> files) {
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
        vehicle.setCarType(normalizeCarType(request.resolvedCarType()));
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
            Vehicle saved = vehicleRepository.save(vehicle);
            saveImages(saved, files);
            return VehicleDtos.VehicleResponse.from(saved);
        }

        if (resolved.legacyPlainText()) {
            vehicle.setBrand(trim(request.brand()));
            vehicle.setModel(trim(request.model()));
            vehicle.setVerificationStatus(VehicleVerificationStatus.APPROVED);
            Vehicle saved = vehicleRepository.save(vehicle);
            saveImages(saved, files);
            return VehicleDtos.VehicleResponse.from(saved);
        }

        // OTHER or suggested custom brand/model present → defer addition until
        // admin approves. No vehicle is created; the draft is carried by a
        // BRAND_MODEL_VERIFICATION request. The request is committed in its own
        // transaction so it survives the exception that signals the FE.
        String brandText = hasText(request.suggestedBrandName()) ? request.suggestedBrandName().trim() : trim(request.brand());
        String modelText = hasText(request.suggestedModelName()) ? request.suggestedModelName().trim() : trim(request.model());
        requiresNewTx.executeWithoutResult(status ->
                createBrandModelDraftRequest(
                        plate,
                        normalizeCarType(request.resolvedCarType()),
                        request.resolvedYear(),
                        brandText,
                        modelText,
                        brand,
                        model));
        throw new ApiException(
                HttpStatus.CONFLICT, "Vehicle brand/model verification required", "BRAND_MODEL_VERIFICATION_REQUIRED");
    }

    @Transactional
    public VehicleDtos.VehicleResponse update(Long id, VehicleDtos.VehicleRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public VehicleDtos.VehicleResponse update(Long id, VehicleDtos.VehicleRequest request, List<MultipartFile> files) {
        Vehicle vehicle = vehicleRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (!vehicle.isActive()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
        requireAdminOrOwner(vehicle);

        if (request.licensePlate() != null && !request.licensePlate().isBlank()) {
            String plate = LicensePlateNormalizer.normalize(request.licensePlate());
            if (vehicleRepository.existsByLicensePlateAndIsActiveTrueAndIdNot(plate, id)) {
                throw new ApiException(HttpStatus.CONFLICT, "License plate already exists");
            }
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
        if (request.carType() != null && !request.carType().isBlank()) {
            vehicle.setCarType(normalizeCarType(request.carType()));
        }

        saveImages(vehicle, files);

        return VehicleDtos.VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleDtos.VehicleResponse delete(Long id) {
        Vehicle vehicle = vehicleRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (!vehicle.isActive()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
        requireAdminOrOwner(vehicle);
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

    /** Creates a BRAND_MODEL_VERIFICATION request that carries the draft of a
     *  vehicle the customer submitted with a custom (OTHER) brand/model. The
     *  actual vehicle is only created when an admin approves it. */
    private void createBrandModelDraftRequest(
            String licensePlate,
            String carType,
            Integer manufactureYear,
            String brandName,
            String modelName,
            VehicleBrand brand,
            VehicleModel model) {
        VehicleAccessRequest accessRequest = new VehicleAccessRequest();
        accessRequest.setRequester(authService.currentUser());
        accessRequest.setLicensePlate(licensePlate);
        accessRequest.setRelationship("BRAND_MODEL_VERIFICATION");
        accessRequest.setNote("Vehicle brand/model verification request");
        accessRequest.setRequestType(VehicleAccessRequestType.BRAND_MODEL_VERIFICATION);
        accessRequest.setSuggestedBrandName(brandName);
        accessRequest.setSuggestedModelName(modelName);
        accessRequest.setBrandRef(brand);
        accessRequest.setModelRef(model);
        accessRequest.setCarType(carType);
        accessRequest.setManufactureYear(manufactureYear);
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

    private void requireAdmin() {
        if (authService.currentUser().getRole() != UserRole.ROLE_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin role is required");
        }
    }

    private void requireAdminOrOwner(Vehicle vehicle) {
        User currentUser = authService.currentUser();
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (!vehicle.getCustomer().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
    }

    private boolean matchesKeyword(Vehicle vehicle, String keyword) {
        if (keyword.isBlank()) {
            return true;
        }
        String haystack = String.join(
                        " ",
                        safe(vehicle.getLicensePlate()),
                        safe(vehicle.getBrand()),
                        safe(vehicle.getModel()),
                        safe(vehicle.getCustomer().getFullName()),
                        safe(vehicle.getCustomer().getPhone()))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
    }

    private Comparator<Vehicle> comparator(String sortBy, String sortOrder) {
        Comparator<Vehicle> comparator = switch (sortBy == null ? "" : sortBy) {
            case "licensePlate" -> Comparator.comparing(Vehicle::getLicensePlate, String.CASE_INSENSITIVE_ORDER);
            case "brand" -> Comparator.comparing(Vehicle::getBrand, String.CASE_INSENSITIVE_ORDER);
            case "model" -> Comparator.comparing(Vehicle::getModel, String.CASE_INSENSITIVE_ORDER);
            case "year", "manufactureYear" -> Comparator.comparing(
                    Vehicle::getManufactureYear, Comparator.nullsLast(Integer::compareTo));
            case "carType" -> Comparator.comparing(Vehicle::getCarType, String.CASE_INSENSITIVE_ORDER);
            case "owner", "customer", "displayName" -> Comparator.comparing(
                    vehicle -> vehicle.getCustomer().getFullName(), String.CASE_INSENSITIVE_ORDER);
            case "updatedAt" -> Comparator.comparing(Vehicle::getUpdatedAt);
            default -> Comparator.comparing(Vehicle::getCreatedAt);
        };
        if ("asc".equalsIgnoreCase(sortOrder)) {
            return comparator;
        }
        return comparator.reversed();
    }

    private String normalizeOptionalCarType(String carType) {
        if (carType == null || carType.isBlank() || "all".equalsIgnoreCase(carType)) {
            return null;
        }
        return normalizeCarType(carType);
    }

    private String normalizeCarType(String carType) {
        String normalized = carType == null || carType.isBlank()
                ? VehicleDtos.DEFAULT_CAR_TYPE
                : carType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("sedan", "suv", "pickup").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid car type");
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void saveImages(Vehicle vehicle, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;

        // Delete existing images
        List<VehicleImage> existingImages = vehicleImageRepository.findByVehicleIdOrderBySortOrderAsc(vehicle.getId());
        for (VehicleImage img : existingImages) {
            fileStorageService.delete(img.getUrl());
        }
        vehicleImageRepository.deleteByVehicleId(vehicle.getId());

        // Save new images
        int sortOrder = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String url = fileStorageService.store(file);
            VehicleImage image = new VehicleImage();
            image.setVehicle(vehicle);
            image.setUrl(url);
            image.setOriginalName(file.getOriginalFilename());
            image.setSortOrder(sortOrder++);
            vehicleImageRepository.save(image);
        }
    }
}
