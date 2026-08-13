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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final FileStorageService fileStorageService;
    private final AuthService authService;

    public VehicleService(VehicleRepository vehicleRepository,
                          VehicleImageRepository vehicleImageRepository,
                          FileStorageService fileStorageService,
                          AuthService authService) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleImageRepository = vehicleImageRepository;
        this.fileStorageService = fileStorageService;
        this.authService = authService;
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
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setColor(request.color());
        vehicle.setManufactureYear(request.resolvedYear());
        vehicle.setCarType(normalizeCarType(request.resolvedCarType()));
        vehicle.setOwnershipStartAt(LocalDateTime.now());
        vehicle = vehicleRepository.save(vehicle);

        saveImages(vehicle, files);

        return VehicleDtos.VehicleResponse.from(vehicle);
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
