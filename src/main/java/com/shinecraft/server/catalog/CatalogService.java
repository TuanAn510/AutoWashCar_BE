package com.shinecraft.server.catalog;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.notification.NotificationService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final ServiceCategoryRepository categoryRepository;
    private final CarWashServiceRepository serviceRepository;
    private final NotificationService notificationService;

    public CatalogService(
            ServiceCategoryRepository categoryRepository,
            CarWashServiceRepository serviceRepository,
            NotificationService notificationService) {
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.CategoryResponse> categories() {
        return categoryRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(CatalogDtos.CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.CategoryResponse> allCategories() {
        return categoryRepository.findAll().stream()
                .map(CatalogDtos.CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ServiceResponse> services() {
        return serviceRepository.findByIsActiveTrueAndCategory_IsActiveTrueOrderByNameAsc().stream()
                .map(CatalogDtos.ServiceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ServiceResponse> allServices() {
        return serviceRepository.findAll().stream()
                .map(CatalogDtos.ServiceResponse::from)
                .toList();
    }

    @Transactional
    public CatalogDtos.CategoryResponse createCategory(CatalogDtos.CategoryRequest request) {
        String name = requireCategoryName(request.name(), null);
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "Service category already exists");
        }
        ServiceCategory category = new ServiceCategory();
        apply(category, request, name);
        return CatalogDtos.CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CatalogDtos.CategoryResponse updateCategory(Long id, CatalogDtos.CategoryRequest request) {
        ServiceCategory category = categoryRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service category not found"));
        String name = requireCategoryName(request.name(), category.getName());
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Service category already exists");
        }
        boolean deactivateServices = category.isActive() && Boolean.FALSE.equals(request.resolvedActive());
        apply(category, request, name);
        if (deactivateServices) {
            serviceRepository.findByCategoryAndIsActiveTrue(category)
                    .forEach(service -> service.setActive(false));
        }
        return CatalogDtos.CategoryResponse.from(category);
    }

    @Transactional
    public CatalogDtos.ServiceResponse createService(CatalogDtos.ServiceRequest request) {
        CarWashService service = new CarWashService();
        apply(service, request, true);
        CarWashService saved = serviceRepository.saveAndFlush(service);
        String priceStr = String.format("%,.0fđ", saved.getPrice());
        notificationService.notifyAdmins(
                "SERVICE",
                "Dịch vụ mới được thêm",
                "Thêm dịch vụ \"" + saved.getName() + "\" - Giá: " + priceStr + ", Thời lượng: " + saved.getDurationMinutes() + " phút.",
                "BOOKING",
                null);
        notificationService.notifyCustomers(
                "SERVICE",
                "Dịch vụ mới vừa ra mắt!",
                "Dịch vụ \"" + saved.getName() + "\" mới vừa được thêm vào hệ thống - Giá: " + priceStr + ". Đặt lịch ngay!",
                "BOOKING",
                null);
        return CatalogDtos.ServiceResponse.from(saved);
    }

    @Transactional
    public CatalogDtos.ServiceResponse updateService(Long id, CatalogDtos.ServiceRequest request) {
        CarWashService service = serviceRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service not found"));
        if (request.version() != null && !request.version().equals(service.getVersion())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Service was updated by another administrator. Refresh and try again");
        }
        apply(service, request, false);
        CarWashService saved = serviceRepository.saveAndFlush(service);
        String priceStr = String.format("%,.0fđ", saved.getPrice());
        notificationService.notifyAdmins(
                "SERVICE",
                "Dịch vụ đã được cập nhật",
                "Cập nhật dịch vụ \"" + saved.getName() + "\" - Giá mới: " + priceStr + ", Thời lượng: " + saved.getDurationMinutes() + " phút.",
                "BOOKING",
                saved.getId());
        notificationService.notifyCustomers(
                "SERVICE",
                "Cập nhật dịch vụ",
                "Dịch vụ \"" + saved.getName() + "\" vừa được cập nhật - Giá: " + priceStr + ". Xem ngay!",
                "BOOKING",
                saved.getId());
        return CatalogDtos.ServiceResponse.from(saved);
    }

    private void apply(ServiceCategory category, CatalogDtos.CategoryRequest request, String name) {
        category.setName(name);
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.resolvedActive() != null) {
            category.setActive(request.resolvedActive());
        }
    }

    private String requireCategoryName(String requestedName, String currentName) {
        String name = requestedName == null ? currentName : requestedName.trim();
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service category name is required");
        }
        return name;
    }

    private void apply(CarWashService service, CatalogDtos.ServiceRequest request, boolean creating) {
        ServiceCategory category = request.categoryId() == null
                ? service.getCategory()
                : categoryRepository
                        .findById(request.categoryId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service category not found"));
        if (category == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service category is required");
        }

        String name = request.name() == null ? service.getName() : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service name is required");
        }
        if (name.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service name must not exceed 120 characters");
        }

        BigDecimal price = request.price() == null ? service.getPrice() : request.price();
        if (price == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service price is required");
        }

        BigDecimal rewardMultiplier = request.rewardMultiplier() == null
                ? service.getRewardMultiplier()
                : request.rewardMultiplier();
        if (rewardMultiplier == null) {
            rewardMultiplier = ServiceRewardPointsPolicy.DEFAULT_MULTIPLIER;
        }
        if (!ServiceRewardPointsPolicy.isSupportedMultiplier(rewardMultiplier)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Reward multiplier must be a whole number between 1 and 5");
        }

        Integer duration = request.resolvedDuration() == null
                ? service.getDurationMinutes()
                : request.resolvedDuration();
        if (duration == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Estimated duration is required");
        }

        boolean active = request.resolvedActive() == null ? service.isActive() : request.resolvedActive();
        if (active && !category.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Active services require an active category");
        }

        boolean duplicate = creating
                ? serviceRepository.existsByCategoryAndNameIgnoreCase(category, name)
                : serviceRepository.existsByCategoryAndNameIgnoreCaseAndIdNot(category, name, service.getId());
        if (duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "Service already exists in this category");
        }

        service.setCategory(category);
        service.setName(name);
        if (request.description() != null) {
            service.setDescription(request.description());
        }
        service.setPrice(price);
        service.setDurationMinutes(duration);
        service.setRewardMultiplier(rewardMultiplier);
        service.setRewardPoints(ServiceRewardPointsPolicy.calculate(price, rewardMultiplier));
        service.setActive(active);
    }
}
