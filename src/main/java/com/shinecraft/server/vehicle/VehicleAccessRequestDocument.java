package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicle_access_request_documents")
public class VehicleAccessRequestDocument extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_request_id")
    private VehicleAccessRequest accessRequest;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 255)
    private String originalName;

    @Column(length = 127)
    private String mimeType;

    @Column(length = 20)
    private String documentType;

    @Column(nullable = false)
    private int sortOrder;
}