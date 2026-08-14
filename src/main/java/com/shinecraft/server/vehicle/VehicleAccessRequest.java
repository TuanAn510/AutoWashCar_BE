package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.BaseEntity;
import com.shinecraft.server.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicle_access_requests")
public class VehicleAccessRequest extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(nullable = false, length = 20)
    private String licensePlate;

    @Column(nullable = false, length = 80)
    private String relationship;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleAccessRequestStatus status = VehicleAccessRequestStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VehicleAccessRequestType requestType = VehicleAccessRequestType.ACCESS_REQUEST;

    @Column(length = 80)
    private String suggestedBrandName;

    @Column(length = 80)
    private String suggestedModelName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private VehicleBrand brandRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private VehicleModel modelRef;

    @Column(length = 20)
    private String carType;

    private Integer manufactureYear;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String reviewNote;

    private LocalDateTime reviewedAt;
}
