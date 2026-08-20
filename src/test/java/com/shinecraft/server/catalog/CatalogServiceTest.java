package com.shinecraft.server.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinecraft.server.common.ApiException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {
    @Mock
    private ServiceCategoryRepository categoryRepository;

    @Mock
    private CarWashServiceRepository serviceRepository;

    @InjectMocks
    private CatalogService catalogService;

    @Test
    void updateServiceRejectsAStaleDatabaseVersionWithoutChangingTheEntity() {
        CarWashService service = service(3L);
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));

        CatalogDtos.ServiceRequest staleRequest = request(2L);

        assertThatThrownBy(() -> catalogService.updateService(10L, staleRequest))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("SERVICE_UPDATE_CONFLICT");
                });

        assertThat(service.getName()).isEqualTo("Current service");
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void updateServiceAcceptsTheCurrentDatabaseVersionAndReturnsIt() {
        CarWashService service = service(3L);
        ServiceCategory category = category();
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(serviceRepository.saveAndFlush(service)).thenReturn(service);

        CatalogDtos.ServiceResponse response = catalogService.updateService(10L, request(3L));

        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.name()).isEqualTo("Updated service");
        assertThat(service.getName()).isEqualTo("Updated service");
    }

    private CarWashService service(Long version) {
        CarWashService service = new CarWashService();
        service.setId(10L);
        service.setCategory(category());
        service.setName("Current service");
        service.setDescription("Current description");
        service.setPrice(BigDecimal.valueOf(100_000));
        service.setDurationMinutes(30);
        service.setActive(true);
        service.setVersion(version);
        return service;
    }

    private ServiceCategory category() {
        ServiceCategory category = new ServiceCategory();
        category.setId(1L);
        category.setName("Washing");
        category.setActive(true);
        return category;
    }

    private CatalogDtos.ServiceRequest request(Long version) {
        return new CatalogDtos.ServiceRequest(
                1L,
                "Updated service",
                "Updated description",
                BigDecimal.valueOf(120_000),
                45,
                45,
                true,
                true,
                version);
    }
}
