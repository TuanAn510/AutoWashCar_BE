package com.shinecraft.server.vehicle;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleAccessRequestDocumentRepository
        extends JpaRepository<VehicleAccessRequestDocument, Long> {
    List<VehicleAccessRequestDocument> findByAccessRequestIdOrderBySortOrderAsc(Long accessRequestId);

    void deleteByAccessRequestId(Long accessRequestId);
}