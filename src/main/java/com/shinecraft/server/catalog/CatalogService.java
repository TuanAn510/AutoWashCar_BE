package com.shinecraft.server.catalog;

import com.shinecraft.server.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final ServiceCategoryRepository categoryRepository;
    private final CarWashServiceRepository serviceRepository;

    public CatalogService(ServiceCategoryRepository categoryRepository, CarWashServiceRepository serviceRepository) {
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
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
        return serviceRepository.findByIsActiveTrueOrderByNameAsc().stream()
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
        if (categoryRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "Service category already exists");
        }
        ServiceCategory category = new ServiceCategory();
        apply(category, request);
        return CatalogDtos.CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CatalogDtos.CategoryResponse updateCategory(Long id, CatalogDtos.CategoryRequest request) {
        ServiceCategory category = categoryRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service category not found"));
        apply(category, request);
        return CatalogDtos.CategoryResponse.from(category);
    }

    @Transactional
    public CatalogDtos.ServiceResponse createService(CatalogDtos.ServiceRequest request) {
        CarWashService service = new CarWashService();
        apply(service, request);
        return CatalogDtos.ServiceResponse.from(serviceRepository.save(service));
    }

    @Transactional
    public CatalogDtos.ServiceResponse updateService(Long id, CatalogDtos.ServiceRequest request) {
        CarWashService service = serviceRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service not found"));
        apply(service, request);
        return CatalogDtos.ServiceResponse.from(service);
    }

    private void apply(ServiceCategory category, CatalogDtos.CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(request.description());
        if (request.resolvedActive() != null) {
            category.setActive(request.resolvedActive());
        }
    }

    private void apply(CarWashService service, CatalogDtos.ServiceRequest request) {
        ServiceCategory category = categoryRepository
                .findById(request.categoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service category not found"));
        service.setCategory(category);
        service.setName(request.name().trim());
        service.setDescription(request.description());
        service.setPrice(request.price());
        if (request.resolvedDuration() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Estimated duration is required");
        }
        service.setDurationMinutes(request.resolvedDuration());
        if (request.resolvedActive() != null) {
            service.setActive(request.resolvedActive());
        }
    }
}
