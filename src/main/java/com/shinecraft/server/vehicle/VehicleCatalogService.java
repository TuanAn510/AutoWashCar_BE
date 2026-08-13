package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleCatalogService {
    private final VehicleBrandRepository brandRepository;
    private final VehicleModelRepository modelRepository;

    public VehicleCatalogService(VehicleBrandRepository brandRepository, VehicleModelRepository modelRepository) {
        this.brandRepository = brandRepository;
        this.modelRepository = modelRepository;
    }

    @Transactional(readOnly = true)
    public List<VehicleCatalogDtos.BrandResponse> activeBrands() {
        return brandRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(VehicleCatalogDtos.BrandResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleCatalogDtos.ModelResponse> activeModels(Long brandId) {
        brandRepository
                .findByIdAndIsActiveTrue(brandId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle brand not found"));
        return modelRepository.findByBrandIdAndIsActiveTrueOrderByNameAsc(brandId).stream()
                .map(VehicleCatalogDtos.ModelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleCatalogDtos.BrandResponse> allBrands() {
        return brandRepository.findAllByOrderByNameAsc().stream()
                .map(VehicleCatalogDtos.BrandResponse::from)
                .toList();
    }

    @Transactional
    public VehicleCatalogDtos.BrandResponse createBrand(VehicleCatalogDtos.BrandRequest request) {
        String name = request.name().trim();
        if (brandRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "Vehicle brand already exists");
        }
        VehicleBrand brand = new VehicleBrand();
        brand.setName(name);
        if (request.resolvedActive() != null) {
            brand.setActive(request.resolvedActive());
        }
        return VehicleCatalogDtos.BrandResponse.from(brandRepository.save(brand));
    }

    @Transactional
    public VehicleCatalogDtos.ModelResponse createModel(Long brandId, VehicleCatalogDtos.ModelRequest request) {
        VehicleBrand brand = brandRepository
                .findById(brandId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle brand not found"));
        String name = request.name().trim();
        if (modelRepository.existsByBrandIdAndNameIgnoreCase(brandId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "Vehicle model already exists for this brand");
        }
        VehicleModel model = new VehicleModel();
        model.setBrand(brand);
        model.setName(name);
        if (request.isNew() != null) {
            model.setNew(request.isNew());
        }
        if (request.resolvedActive() != null) {
            model.setActive(request.resolvedActive());
        }
        return VehicleCatalogDtos.ModelResponse.from(modelRepository.save(model));
    }
}
